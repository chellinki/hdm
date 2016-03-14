package org.nicta.wdy.hdm.scheduling

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue, Semaphore, TimeUnit}

import akka.pattern.ask
import akka.util.Timeout
import com.baidu.bpit.akka.actors.worker.WorkActor
import org.nicta.wdy.hdm.executor._
import org.nicta.wdy.hdm.functions.{ParUnionFunc, ParallelFunction}
import org.nicta.wdy.hdm.io.{AkkaIOManager, Path}
import org.nicta.wdy.hdm.message.AddTaskMsg
import org.nicta.wdy.hdm.model._
import org.nicta.wdy.hdm.storage.{Computed, HDMBlockManager}
import org.nicta.wdy.hdm.utils.Logging

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.reflect.ClassTag

/**
  * Created by Tiantian on 2014/12/1.
 */
trait Scheduler {

  implicit val executorService:ExecutionContext

  def submitJob(appId:String, version:String, hdms:Seq[HDM[_,_]]): Future[HDM[_,_]]

  def addTask[R](task:ParallelTask[R]):Promise[HDM[_, R]]

  def taskSucceeded(appId: String, taskId: String, func: String, blks: Seq[HDM[_, _]]): Unit

  def init()

  def startup()

  def stop()

  protected def scheduleTask [R:ClassTag](task:ParallelTask[R], workerPath:String):Promise[HDM[_, R]]

}

/**
 *
 * @param candidatesMap
 * @param executorService
 */
class SimpleActorBasedScheduler(val candidatesMap: java.util.Map[String, AtomicInteger] = new ConcurrentHashMap[String, AtomicInteger])
                               (implicit val executorService:ExecutionContext) extends Scheduler{

  this: WorkActor =>

  import scala.collection.JavaConversions._


  val blockManager = HDMBlockManager() //todo get from HDMContext

  val ioManager = new AkkaIOManager //todo get from HDMContext

  val appManager = new AppManager //todo get from HDMContext

  val appBuffer: java.util.Map[String, ListBuffer[ParallelTask[_]]] = new ConcurrentHashMap[String, ListBuffer[ParallelTask[_]]]()

  val taskQueue = new LinkedBlockingQueue[ParallelTask[_]]()

  val promiseMap = new ConcurrentHashMap[String, Promise[_]]()

  val workingSize = new Semaphore(0)

  val isRunning = new AtomicBoolean(false)

  implicit val timeout = Timeout(5L, TimeUnit.MINUTES)

  override protected def scheduleTask[R: ClassTag](task: ParallelTask[R], workerPath:String): Promise[HDM[_, R]] = {
    val promise = promiseMap.get(task.taskId).asInstanceOf[Promise[HDM[_, R]]]


    if (task.func.isInstanceOf[ParUnionFunc[_]]) {
      //copy input blocks directly
      val blks = task.input.map(h => blockManager.getRef(h.id))
      taskSucceeded(task.appId, task.taskId, task.func.toString, blks)
    } else {
      // run job, assign to remote or local node to execute this task
      val updatedTask = task match {
        case singleInputTask:Task[_,R] =>
          val blkSeq = singleInputTask.input.map(h => blockManager.getRef(h.id)).flatMap(_.blocks)
          val inputDDMs = blkSeq.map(bl => blockManager.getRef(Path(bl).name))
          singleInputTask.asInstanceOf[Task[singleInputTask.inType.type, R]]
            .copy(input = inputDDMs.asInstanceOf[Seq[HDM[_, singleInputTask.inType.type]]])
        case twoInputTask:TwoInputTask[_, _, R] =>
          val blkSeq1 = twoInputTask.input1.map(h => blockManager.getRef(h.id)).flatMap(_.blocks)
          val blkSeq2 = twoInputTask.input2.map(h => blockManager.getRef(h.id)).flatMap(_.blocks)
          val inputDDM1 = blkSeq1.map(bl => blockManager.getRef(Path(bl).name))
          val inputDDM2 = blkSeq2.map(bl => blockManager.getRef(Path(bl).name))
          twoInputTask.asInstanceOf[TwoInputTask[twoInputTask.inTypeOne.type, twoInputTask.inTypeTwo.type, R]]
            .copy(input1 = inputDDM1.asInstanceOf[Seq[HDM[_, twoInputTask.inTypeOne.type]]], input2 = inputDDM2.asInstanceOf[Seq[HDM[_, twoInputTask.inTypeTwo.type]]])
      }
      workingSize.acquire(1)
      log.info(s"Task has been assigned to: [$workerPath] [${task.taskId + "__" + task.func.toString}}] ")
      val future = if (Path.isLocal(workerPath)) ClusterExecutor.runTask(updatedTask)
      else runRemoteTask(workerPath, updatedTask)
      log.info(s"A task has been scheduled: [${task.taskId + "__" + task.func.toString}}] ")
    }

    promise
  }

  override def stop(): Unit = {
    isRunning.set(false)
  }

  override def startup(): Unit = {
    isRunning.set(true)
    while (isRunning.get) {
      val task = taskQueue.take()
      log.info(s"A task has been scheduling: [${task.taskId + "__" + task.func.toString}}] ")
      schedule(task)
    }
  }

  private def schedule[R: ClassTag](task: ParallelTask[R]): Promise[HDM[_, R]] = {
    val promise = promiseMap.get(task.taskId).asInstanceOf[Promise[HDM[_, R]]]


    if (task.func.isInstanceOf[ParUnionFunc[_]]) {
      //copy input blocks directly
      val blks = task.input.map(h => blockManager.getRef(h.id))
      taskSucceeded(task.appId, task.taskId, task.func.toString, blks)
    } else {
      // run job, assign to remote or local node to execute this task
      val updatedTask = task match {
        case singleInputTask:Task[_,R] =>
          val blkSeq = singleInputTask.input.map(h => blockManager.getRef(h.id)).flatMap(_.blocks)
          val inputDDMs = blkSeq.map(bl => blockManager.getRef(Path(bl).name))
          singleInputTask.asInstanceOf[Task[singleInputTask.inType.type, R]]
            .copy(input = inputDDMs.asInstanceOf[Seq[HDM[_, singleInputTask.inType.type]]])
        case twoInputTask:TwoInputTask[_, _, R] =>
          val blkSeq1 = twoInputTask.input1.map(h => blockManager.getRef(h.id)).flatMap(_.blocks)
          val blkSeq2 = twoInputTask.input2.map(h => blockManager.getRef(h.id)).flatMap(_.blocks)
          val inputDDM1 = blkSeq1.map(bl => blockManager.getRef(Path(bl).name))
          val inputDDM2 = blkSeq2.map(bl => blockManager.getRef(Path(bl).name))
          twoInputTask.asInstanceOf[TwoInputTask[twoInputTask.inTypeOne.type, twoInputTask.inTypeTwo.type, R]]
            .copy(input1 = inputDDM1.asInstanceOf[Seq[HDM[_, twoInputTask.inTypeOne.type]]], input2 = inputDDM2.asInstanceOf[Seq[HDM[_, twoInputTask.inTypeTwo.type]]])
      }
      workingSize.acquire(1)
      var workerPath = Scheduler.findPreferredWorker(updatedTask, candidatesMap )
      while (workerPath == null || workerPath == ""){ // wait for available workers
        log.info(s"no worker available for task[${task.taskId + "__" + task.func.toString}}] ")
        workerPath = Scheduler.findPreferredWorker(updatedTask, candidatesMap)
        Thread.sleep(50)
      }
      log.info(s"Task has been assigned to: [$workerPath] [${task.taskId + "__" + task.func.toString}}] ")
      val future = if (Path.isLocal(workerPath)) ClusterExecutor.runTask(updatedTask)
      else runRemoteTask(workerPath, updatedTask)
      log.info(s"A task has been scheduled: [${task.taskId + "__" + task.func.toString}}] ")
    }

    promise
  }

  override def init(): Unit = {
    isRunning.set(false)
    taskQueue.clear()
    val totalSlots = candidatesMap.map(_._2.get()).sum
    workingSize.release(totalSlots)
  }

  override def addTask[R](task: ParallelTask[R]): Promise[HDM[_, R]] = {
    val promise = Promise[HDM[_, R]]()
    promiseMap.put(task.taskId, promise)
    if (!appBuffer.containsKey(task.appId))
      appBuffer.put(task.appId, new ListBuffer[ParallelTask[_]])
    val lst = appBuffer.get(task.appId)
    lst += task
    triggerTasks(task.appId)
    promise
  }

  def jobReceived(appId:String, version:String, hdm:HDM[_,_], parallelism:Int): Future[HDM[_, _]] = {
    appManager.addApp(appId, hdm)
    val plan = HDMContext.explain(hdm, parallelism)
    appManager.addPlan(appId, plan)
    submitJob(appId, version, plan)
  }

  //todo move and implement at job compiler
  override def submitJob(appId: String, version:String, hdms: Seq[HDM[_, _]]): Future[HDM[_, _]] = {
    hdms.map { h =>
      blockManager.addRef(h)
      val task = Task(appId = appId,
        version = version,
        taskId = h.id,
        input = h.children.asInstanceOf[Seq[HDM[_, h.inType.type]]],
        func = h.func.asInstanceOf[ParallelFunction[h.inType.type, h.outType.type]],
        dep = h.dependency,
        partitioner = h.partitioner.asInstanceOf[Partitioner[h.outType.type ]])
      addTask(task)
    }.last.future
  }



  def taskSucceeded(appId: String, taskId: String, func: String, blks: Seq[HDM[_, _]]): Unit = {
    val blockSeq = blks.flatMap(_.blocks)
    val ref = HDMBlockManager().getRef(taskId) match {
      case dfm: DFM[_ , _] => dfm.copy(blocks = blockSeq, state = Computed)
      case ddm: DDM[_ , _] => ddm.copy(state = Computed)
    }
    blockManager.addRef(ref)
    HDMContext.declareHdm(Seq(ref))
    log.info(s"A task is succeeded : [${taskId + "_" + func}}] ")
    val promise = promiseMap.remove(taskId).asInstanceOf[Promise[HDM[_, _]]]
    if (promise != null && !promise.isCompleted){
      promise.success(ref.asInstanceOf[HDM[_, _]])
      log.info(s"A promise is triggered for : [${taskId + "_" + func}}] ")
    }
    else if (promise eq null) {
      log.warning(s"no matched promise found: $taskId")
      log.debug(s"current promise map: ${promiseMap.keys().toSeq}")
    }
    triggerTasks(appId)
  }


  private def triggerTasks(appId: String) = {
    if (appBuffer.containsKey(appId)) {
      val seq = appBuffer.get(appId)
      synchronized {
        if (seq.nonEmpty) {
          //find tasks that all inputs have been computed
          val tasks = seq.filter(t =>
            if (t.input eq null) false
            else try {
              t.input.forall{in =>
                val hdm = HDMBlockManager().getRef(in.id)
                if(hdm ne null)
                  hdm.state.eq(Computed)
                else false
              }
            } catch {
              case ex: Throwable => log.error(ex, s"Got exception on $t"); false
            }
          )
          if ((tasks ne null) && tasks.nonEmpty) {
            seq --= tasks
            tasks.foreach(taskQueue.put)
            log.info(s"New tasks have has been activated: [${tasks.map(t => (t.taskId, t.func)).mkString(",")}}] ")
          }
        }
      }
    }

  }

  private def runRemoteTask[R: ClassTag](workerPath: String, task: ParallelTask[R]): Future[Seq[String]] = {
    val future = (context.actorSelection(workerPath) ? AddTaskMsg(task)).mapTo[Seq[String]]
    future
  }





}

object Scheduler extends Logging{


  def findPreferredWorker(task: ParallelTask[_], candidatesMap: mutable.Map[String, AtomicInteger]): String = try {

    //    val inputLocations = task.input.flatMap(hdm => HDMBlockManager().getRef(hdm.id).blocks).map(Path(_))
    val inputLocations = task.input.flatMap { hdm =>
      val nhdm = HDMBlockManager().getRef(hdm.id)
      if (nhdm.preferLocation == null)
        nhdm.blocks.map(Path(_))
      else Seq(nhdm.preferLocation)
    }
//    log.info(s"Block prefered input locations:${inputLocations.mkString(",")}")
    val candidates =
      if (task.dep == OneToN || task.dep == OneToOne) Scheduler.getAvailablePaths(candidatesMap) // for parallel tasks
      else Scheduler.getFreestWorkers(candidatesMap) // for shuffle tasks

    //find closest worker from candidates
    if (candidates.size > 0) {
      val workerPath = Path.findClosestLocation(candidates, inputLocations).toString
      candidatesMap(workerPath).decrementAndGet() // reduce slots of worker
      workerPath
    } else ""
  } catch {
    case e: Throwable =>
      log.error(s"failed to find worker for task:${task.taskId}")
      ""
  }

  def findPreferredTask(tasks: Seq[Task[_, _]], candidate: Path): Int = try {
    val inputLocations = tasks.map {
      _.input.flatMap { hdm =>
        val nhdm = HDMBlockManager().getRef(hdm.id)
        if (nhdm.preferLocation == null)
          nhdm.blocks.map(Path(_))
        else Seq(nhdm.preferLocation)
      }
    }
    val weights = tasks.map{ t=>
      t.input.map{ _.blockSize / 1024 * 1024F // MB
    }}
    Path.findClosestCluster(candidate, inputLocations, weights)
  } catch {
    case e: Throwable =>
      log.error(s"failed to find task for worker:$candidate")
      0
  }


  def getAvailablePaths(candidateMap: mutable.Map[String, AtomicInteger]): Seq[Path] = {

    candidateMap.filter(t => t._2.get() > 0).map(s => Path(s._1)).toSeq
  }
  
  def getAllAvailableWorkers(candidateMap: mutable.Map[String, AtomicInteger]): Seq[Path] = {

    candidateMap.filter(t => t._2.get() > 0).map(s => Seq.fill(s._2.get()){Path(s._1)}).flatten.toSeq
  }

  def getFreestWorkers(candidateMap: mutable.Map[String, AtomicInteger]): Seq[Path] = {
    val workers = mutable.Buffer.empty[Path]
    val sorted = candidateMap.filter(t => t._2.get() > 0).toSeq.sortBy(_._2.get())(ord = Ordering[Int].reverse).iterator
    if(sorted.hasNext){
      var cur = sorted.next()
      workers += Path(cur._1)
      var next:(String, AtomicInteger) = null
      while(sorted.hasNext) {
        next = sorted.next()
        if (next != null && (next._2.get >= cur._2.get())) {
          workers += Path(next._1)
          cur = next
        }
      }
    }
    workers
  }
}