package org.nicta.wdy.hdm.io

import com.baidu.bpit.akka.server.SmsSystem
import org.nicta.wdy.hdm.model.DDM
import org.nicta.wdy.hdm.planing.Utils

import scala.util.Try
import scala.collection.mutable.Buffer

/**
 * Created by Tiantian on 2014/5/26.
 */
class Path(val protocol:String, val absPath:String, scope:String = "") extends Serializable {


  lazy val name = if (absPath.contains("/")) absPath.substring(absPath.lastIndexOf("/") + 1)
                  else absPath

  lazy val parent = this.toString.dropRight(name.length + 1)

  lazy val address :String = {
    if(absPath.startsWith("/")) "localhost"
    else if(absPath.contains("/")) {
      absPath.substring(0, absPath.indexOf("/"))
    } else absPath
  }

  lazy val (host:String, port:Int) = {
    if(address.contains(":")){
      val tup = address.splitAt(address.indexOf(":"))
      (tup._1, tup._2.substring(1).toInt)
    } else ("", null)
  }

  def relativePath = {
    absPath.drop(address.length)
  }

  override def toString: String = protocol + scope + absPath

}

object Path {

  val HDM = "hdm://"

  val AKKA = "akka.tcp://"

  val FILE = "file://"

  val HDFS = "hdfs://"

  val THCHYON = "tychyon://"

  def apply(protocol:String, host:String, port:Int, localPath:String) = {
    val path = if(localPath.startsWith("/")) localPath
                else "/" + localPath
    new Path(protocol, s"$host:$port$path")
  }

  def apply(protocol: String, localPath:String) = {
    val path = if(localPath.startsWith("/")) localPath
    else "/" + localPath
    new Path(protocol, path)
  }

  def apply(path:String) = {
    val idx = path.indexOf("://")

    val tuple = if(idx >=0) path.splitAt(idx + 3)
                else ("", path)
    val (scope, absPath) = if(tuple._2.contains("@")){
      val i = tuple._2.indexOf("@")
      tuple._2.splitAt(i+1)
    } else ("", tuple._2)
    new Path(tuple._1, absPath, scope)
  }

  def isLocal(path:String):Boolean = isLocal(Path(path))

  def isLocal(path: Path):Boolean = {
    //todo check local
    if(path.address == null || path.address.equals("")) true
    else path.address == Path(SmsSystem.physicalRootPath).address
  }


  def calculateDistance(src:Path, target:Path):Double = {
    
    if(src.address == target.address) 1
    else {
      val srcSeq = src.address.split("\\.|:|/")
      val tarSeq = target.address.split("\\.|:|/")
      val tarLen = tarSeq.length
      var cur = 0
      while(cur < srcSeq.size && srcSeq(cur)== tarSeq(cur)){
        cur += 1
      }
      tarLen / (cur + 0.01D)
    }
  }

  def calculateDistance(paths: Seq[Path], targetSet: Seq[Path]):Seq[Double] = {
    paths.map{p => // for each path compute the distance vector of target Set
      val vec = targetSet.map(t =>Path.calculateDistance(p, t) )
      vec.sum // return total distance
    }
  }

  /**
   * find out cloest the path that has minimum distance to target Set
   * @param paths
   * @param targetSet
   * @return
   */
  def findClosestLocation(paths: Seq[Path], targetSet: Seq[Path]): Path = {
    val distanceVec = calculateDistance(paths, targetSet)
    val minimum = distanceVec.min
    paths(distanceVec.indexOf(minimum))
  }

  def groupPathBySimilarity(paths:Seq[Path], n:Int) = {
    val avg = paths.size/n
    paths.sortWith( (p1,p2) => path2Int(p1) < path2Int(p2)).grouped(avg.toInt).toSeq
  }

  def groupDDMByLocation(ddms:Seq[DDM[String,String]], n:Int) = {
//    val avg = ddms.size/n
//    ddms.sortWith( (p1,p2) => path2Int(p1.preferLocation) < path2Int(p2.preferLocation)).grouped(avg.toInt).toSeq
    val ddmMap = ddms.map(d => (d.preferLocation -> d)).toMap
    val paths = ddms.map(_.preferLocation)
    val grouped = groupPathBySimilarity(paths, n)
    grouped.map{seq =>
      seq.map(p => ddmMap(p))
    }
  }

  def groupPathByBoundary(paths:Seq[Path], n:Int) = {
    val sorted = paths.sortWith( (p1,p2) => path2Int(p1) < path2Int(p2)).iterator
    val boundery = 256 << 8 + 256
    val ddmBuffer = Buffer.empty[Buffer[Path]]
    var buffer = Buffer.empty[Path]
    val total = paths.size.toFloat

    if(sorted.hasNext){
      var cur = sorted.next()
      buffer += cur
      while (sorted.hasNext) {
        val next = sorted.next()
        if ((path2Int(next) - path2Int(cur)) >= boundery ){
          ddmBuffer += buffer
          buffer = Buffer.empty[Path] += next
        } else {
          buffer += next
        }
        cur = next
      }
      ddmBuffer += buffer
    }
    // subGrouping in each bounded group
    val distribution = ddmBuffer.map(seq => Math.round( (seq.size/total) * n))
    val finalRes = Buffer.empty[Buffer[Path]]
    for{
      i <- 0 until ddmBuffer.size
    }{
      val seq = ddmBuffer(i)
      val groupSize = distribution(i)
      finalRes ++= (Utils.orderKeepingGroup(seq, groupSize))
    }
    finalRes
  }

  def groupDDMByBoundary(ddms:Seq[DDM[String,String]], n:Int) ={
    val ddmMap = ddms.map(d => (d.preferLocation -> d)).toMap
    val paths = ddms.map(_.preferLocation)
    val grouped = groupPathByBoundary(paths, n)
    grouped.map{seq =>
      seq.map(p => ddmMap(p))
    }
  }

  def path2Int(p:Path):Long = {
    Try {
      val ipSegs = p.address.split("\\.|:|/").take(4).map(_.toLong)
      (ipSegs(0) << 24) + (ipSegs(1) << 16) + (ipSegs(2) << 8) + ipSegs(3)
    } getOrElse(0)
  }

  def path2Int(p:String):Long = {
    path2Int(Path(p))
  }


}
