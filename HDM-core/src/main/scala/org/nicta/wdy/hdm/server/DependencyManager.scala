package org.nicta.wdy.hdm.server

import java.net.URLClassLoader
import java.nio.charset.Charset
import java.util.concurrent.{ConcurrentHashMap, Executors}

import ch.qos.logback.classic.pattern.ClassOfCallerConverter
import com.baidu.bpit.akka.server.SmsSystem
import org.nicta.wdy.hdm.executor.{HDMContext, DynamicDependencyThreadFactory}
import org.nicta.wdy.hdm.message.AddApplication
import org.nicta.wdy.hdm.server.provenance.ApplicationTrace
import org.nicta.wdy.hdm.utils.{DynamicURLClassLoader, Logging}

import java.io.File
import java.nio.file._

import scala.collection.mutable

/**
 * Created by tiantian on 3/03/16.
 */
class DependencyManager (val dependencyBasePath:String, val historyManager: ProvenanceManager) extends Serializable with Logging{
  
  import  scala.collection.JavaConversions._
  
  val classLoaderMap:mutable.Map[String, DynamicURLClassLoader] = new ConcurrentHashMap[String, DynamicURLClassLoader]()

  def appLogPath = s"$dependencyBasePath/app/.dep"

  def depLogPath = s"$dependencyBasePath/dep/.dep"

  def getAppPath(appName:String, version:String):String =  s"$dependencyBasePath/app/$appName/${version}/${appName}-${version}.jar"

  def getDepPath(appName:String, version:String):String = s"$dependencyBasePath/dep/$appName/${version}"

  def getSharedDep():String = s"$dependencyBasePath/shared"
  
  def appId(appName:String, version:String) = s"$appName#$version"

  def unwrapAppId(appId:String) = {
    val arr = appId.split("#")
    arr(0) -> arr(1)
  }

  private def loadDepFromFile(path:Path, autoCreate:Boolean = true): Unit = {
    if(Files.exists(path)){
      val tuples = Files.readAllLines(path, Charset.forName("UTF-8")).filter(_.contains("@")).map{line =>
        val arr = line.split("@")
        (arr(0), arr(1))
      }
      tuples.foreach{ t =>
        val (appName, version) = unwrapAppId(t._1)
        addDeptoLoader(appName, version, Array(new java.net.URL(t._2)))
        log.info(s"load dependency ${t._2} for $appName#$version")
      }
    } else {
      if(autoCreate){
        Files.createDirectories(path.getParent)
        Files.createFile(path)
      }
    }
  }

  private def writeDepToFile(appName:String, version:String, urls:Array[java.net.URL], path:Path): Unit = {
    import  scala.collection.JavaConversions._
    if(Files.notExists(path)){
        Files.createDirectories(path.getParent)
        Files.createFile(path)
    }
    val lines = urls.map(url => s"$appName#$version@$url").toList
    Files.write(path, lines, Charset.forName("UTF-8"), StandardOpenOption.APPEND)
  }

  def init(): this.type ={
    val depFile = Paths.get(depLogPath)
    val appFile = Paths.get(appLogPath)
    loadDepFromFile(depFile)
    loadDepFromFile(appFile)
    this
  }
  
  def addDeptoLoader(appName:String, version:String, urls:Array[java.net.URL]) = {
    val id = appId(appName, version)
    if(classLoaderMap.contains(id)){
      urls.foreach(classLoaderMap(id).addURL(_))
    } else {
      classLoaderMap += (id -> new DynamicURLClassLoader(urls, ClassLoader.getSystemClassLoader))
    }
  }

  def getClassLoader(appName:String, version:String) = {
    val id = appId(appName, version)
    if(classLoaderMap.contains(id)){
      classLoaderMap(id)
    } else DynamicDependencyThreadFactory.defaultClassLoader()
  }

  def getAllDepFiles(appName:String, version:String):Array[File] = {
    val depPath = new File(getDepPath(appName, version))
    if(depPath.exists() && depPath.isDirectory){
      depPath.listFiles() ++ new File(getSharedDep).listFiles()
    } else new File(getSharedDep).listFiles()
  }

  //add dependency lib to a job from local
  def addDepFromLocal(appName:String, version:String, srcFile:String, author:String = "defaultUser"): Unit = {
    val src = Paths.get(srcFile)
    val target = Paths.get(getDepPath(appName, version))
    if(Files.notExists(target)){
      Files.createDirectories(target.getParent)
      Files.createFile(target)
    }
    Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING)
    val current =  System.currentTimeMillis()
    val trace = ApplicationTrace(appName, version, author, current, Seq(srcFile))
    historyManager.aggregateAppTrace(trace)
  }

  //add dependency lib to a lib
  def addDep(appName:String, version:String, depName:String, depBytes:Array[Byte], author:String = "defaultUser", global:Boolean = false): Unit = {
    val target = Paths.get(getDepPath(appName, version)+ "/" + depName)
    if(Files.notExists(target)){
      Files.createDirectories(target.getParent)
      Files.createFile(target)
    }
    Files.write(target, depBytes, StandardOpenOption.CREATE)
    val current =  System.currentTimeMillis()
    val trace = ApplicationTrace(appName, version, author, current, Seq(target.toString))
    historyManager.aggregateAppTrace(trace)
    val dep = Array(target.toUri.toURL)
    if(global){
      DynamicDependencyThreadFactory.addGlobalDependency(dep)
//      DependencyManager.loadGlobalDependency(dep)
    } else {
      addDeptoLoader(appName, version, dep)
      val appFile = Paths.get(depLogPath)
      writeDepToFile(appName, version, dep, appFile)
    }
  }

  // submit a job with version from local file
  def submitFromLocal(appName:String, version:String, srcFile:String, author:String = "defaultUser"): Unit = {
    val src = Paths.get(srcFile)
    val target = Paths.get(getAppPath(appName, version))
    if(Files.notExists(target)){
      Files.createDirectories(target.getParent)
      Files.createFile(target)
    }
    Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING)
    val current =  System.currentTimeMillis()
    val trace = ApplicationTrace(appName, version, author, current, Seq(srcFile))
    historyManager.aggregateAppTrace(trace)
  }

  // submit a job with bytes
  def submit(appName:String, version:String, depBytes:Array[Byte], author:String = "defaultUser", global:Boolean = false)= {
    val target = Paths.get(getAppPath(appName, version))
    if(Files.exists(target)){
      Files.write(target, depBytes, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
    } else {
      if(Files.notExists(target.getParent))
        Files.createDirectories(target.getParent)
      Files.write(target, depBytes, StandardOpenOption.CREATE)
    }
    val current =  System.currentTimeMillis()
    val trace = ApplicationTrace(appName, version, author, current, Seq(target.toString))
    historyManager.aggregateAppTrace(trace)
    val dep = Array(target.toUri.toURL)
    if(global) {
      DynamicDependencyThreadFactory.addGlobalDependency(dep)
//      DependencyManager.loadGlobalDependency(dep)
    } else {
      addDeptoLoader(appName, version, dep)
      val appFile = Paths.get(appLogPath)
      writeDepToFile(appName, version, dep, appFile)
    }
  }
}

object DependencyManager {

  lazy val defaultDepManager = new DependencyManager(HDMContext.DEFAULT_DEPENDENCY_BASE_PATH, ProvenanceManager()).init()

  def apply() = {
    defaultDepManager
  }

  def loadDependency(path:String): Unit = {
    val file = new File(path)
    if(file.exists()){
      val parentLoader = Thread.currentThread().getContextClassLoader
      val urls = if(file.isDirectory){
        file.listFiles().map(_.toURI.toURL)
      } else Array(file.toURI.toURL)
      val classLoader = new URLClassLoader(urls, parentLoader)
      Thread.currentThread().setContextClassLoader(classLoader)
    }
  }

  @throws("Couldn't add URLs to class loader")
  def loadGlobalDependency(urls:Array[java.net.URL]): Unit = {
    val sysLoader = ClassLoader.getSystemClassLoader.asInstanceOf[URLClassLoader]
    val sysCls = classOf[URLClassLoader]
    val parameters = classOf[java.net.URL]
    val method = sysCls.getDeclaredMethod("addURL", parameters)
    method.setAccessible(true)
    urls.foreach(url => method.invoke(sysLoader, url))
  }

  @throws("Couldn't read file from file path.")
  def submitAppByPath(master: String, appName: String, version: String, filePath: String, author: String): Unit = {
    val file = new File(filePath).toPath
    val bytes = Files.readAllBytes(file)
    val msg = AddApplication(appName, version, bytes, author)
    SmsSystem.forwardMsg(master, msg)
  }
}