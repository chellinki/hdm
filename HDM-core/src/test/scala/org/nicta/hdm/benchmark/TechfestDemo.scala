package org.nicta.hdm.benchmark

import org.junit.{After, Test}
import org.nicta.wdy.hdm.benchmark.{IterationBenchmark, KVBasedPrimitiveBenchmark}
import org.nicta.wdy.hdm.executor.HDMContext
import org.nicta.wdy.hdm.executor.HDMContext._
import com.baidu.bpit.akka.messages.{AddMsg, Query}
import org.nicta.wdy.hdm.io.Path
import org.nicta.wdy.hdm.model.HDM
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Success, Failure}
/**
 * Created by tiantian on 17/02/15.
 */
class TechfestDemo {

  val text =
    """
        this is a word count text
        this is line 2
        this is line 3
    """.split("\\s+")

  val text2 =
    """
        this is a word count text
        this is line 4
        this is line 5
        this is line 6
        this is line 7
    """.split("\\s+")


  @Test
  def testHDFSExecution(): Unit = {
    HDMContext.init(leader = "akka.tcp://masterSys@127.0.1.1:8999/user/smsMaster")
    Thread.sleep(1000)
    val path = Path("hdfs://127.0.0.1:9001/user/spark/benchmark/partial/rankings")
//    val path = Path("hdfs://127.0.0.1:9001/user/spark/benchmark/micro/uservisits")
    val hdm = HDM(path, false)

    val wordCount = hdm.map{ w =>
      val as = w.split(",");
      (as(0).substring(0,3), as(1).toFloat)
    }
//      .groupBy(_._1)
        .reduceByKey(_ + _)
//      .findByKey(_.startsWith("s"))
      //.map(t => (t._1, t._2.map(_._2).reduce(_+_)))
//      .groupReduce(_._1, (t1,t2) => (t1._1, t1._2 + t2._2))


    wordCount.sample(20)(2) onComplete {
      case Success(hdm) =>
        println("Job completed and received response:" + hdm)
        hdm.foreach(println(_))
      case Failure(t) =>
        println("Job failed because of: " + t)
        t.printStackTrace()
    }

    Thread.sleep(50000000)
  }

  @Test
  def testIterations(): Unit ={
    val context = "akka.tcp://masterSys@127.0.1.1:8999/user/smsMaster"
    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/partial/rankings"
    //    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/micro/uservisits"
    val parallelism = 2
    HDMContext.NETTY_BLOCK_SERVER_PORT = 9092
    HDMContext.init(leader = context)
    Thread.sleep(1500)

    val benchmark = new IterationBenchmark
    benchmark.testGeneralIteration(data, parallelism)
  }


  @Test
  def testPrimitiveBenchMark(): Unit ={
    val context = "akka.tcp://masterSys@127.0.1.1:8999/user/smsMaster"
    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/partial/rankings"
//    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/micro/uservisits"
    val parallelism = 1
    val len = 3
//    val benchmark = new KVBasedPrimitiveBenchmark(context)
    val benchmark = new KVBasedPrimitiveBenchmark(context = context, kIndex = 0, vIndex = 1)
    HDMContext.NETTY_BLOCK_SERVER_PORT = 9092
    HDMContext.init(leader = "akka.tcp://masterSys@127.0.1.1:8999/user/smsMaster")
    Thread.sleep(1500)
    val hdm =
//    benchmark.testGroupBy(data,len, parallelism)
//    benchmark.testMultipleMap(data,len, parallelism)
    benchmark.testMultiMapFilter(data,len, parallelism, "a")
//    benchmark.testFindByKey(data,len, parallelism, "a")
//    benchmark.testReduceByKey(data,len, parallelism)
//    benchmark.testMap(data,len, parallelism)

    onEvent(hdm, "compute")(parallelism)
    Thread.sleep(50000000)
  }

  def onEvent(hdm:HDM[_,_], action:String)(implicit parallelism:Int) = action match {
    case "compute" =>
      val start = System.currentTimeMillis()
      hdm.compute(parallelism).map { hdm =>
        println(s"Job completed in ${System.currentTimeMillis() - start} ms. And received response: ${hdm.id}")
        hdm.blocks.foreach(println(_))
        System.exit(0)
      }
    case "sample" =>
      //      val start = System.currentTimeMillis()
      hdm.sample(25).map(iter => iter.foreach(println(_)))
    case "collect" =>
      val start = System.currentTimeMillis()
      val itr = hdm.collect()
      println(s"Job completed in ${System.currentTimeMillis() - start} ms. And received results: ${itr.size}")
    case x =>
  }


  @Test
  def testCache(): Unit ={
    val context = "akka.tcp://masterSys@127.0.1.1:8999/user/smsMaster"
    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/partial/rankings"
    //    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/micro/uservisits"
    val parallelism = 1
    HDMContext.NETTY_BLOCK_SERVER_PORT = 9092
    HDMContext.init(leader = context)
    Thread.sleep(1500)

    val benchmark = new IterationBenchmark
    benchmark.testIterationWithCache(data, parallelism)
  }


  @Test
  def testCacheExplain(): Unit ={
    val context = "akka.tcp://masterSys@127.0.1.1:8999/user/smsMaster"
    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/partial/rankings"
    //    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/micro/uservisits"
    val parallelism = 1
    HDMContext.NETTY_BLOCK_SERVER_PORT = 9092
    HDMContext.init(leader = context)
    Thread.sleep(1500)

    var aggregation = 0F
    val path = Path(data)
//    val hdm = HDM(path)
//    HDMContext.explain(HDM(path), parallelism).foreach(println(_))
    val hdm = HDM(path).cache(parallelism)
    hdm.children.foreach(println(_))
//    println(hdm)
    for(i <- 1 to 3) {
      val start = System.currentTimeMillis()
      val vOffset = 1 // only avaliable in this scope, todo: support external variables
      val agg = aggregation
      val computed = hdm.map{ w =>
        val as = w.split(",")
        as(vOffset).toFloat + i*agg
      }
//      val res = computed.collect()(parallelism)
//      println(res.size)
//      res.foreach(println(_))
//      aggregation += res.sum
      HDMContext.explain(computed, parallelism).foreach(println(_))
      val end = System.currentTimeMillis()
      println(s"Time consumed for iteration $i : ${end - start} ms.")
      Thread.sleep(100)
    }
    Thread.sleep(300)
  }


  @Test
  def testRegression():Unit = {
    val context = "akka.tcp://masterSys@127.0.1.1:8999/user/smsMaster"
    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/partial/rankings"
    //    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/micro/uservisits"
//    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/1node/weather"
    val parallelism = 1
    HDMContext.NETTY_BLOCK_SERVER_PORT = 9092
    HDMContext.init(leader = context)
    Thread.sleep(1500)

    val benchmark = new IterationBenchmark(1, 1)
    benchmark.testLinearRegression(data, 1, parallelism)
  }

  @Test
  def testWeatherLRegression():Unit = {
    val context = "akka.tcp://masterSys@127.0.1.1:8999/user/smsMaster"
    //    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/partial/rankings"
    //    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/micro/uservisits"
    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/1node/weather"
    val parallelism = 1
    HDMContext.NETTY_BLOCK_SERVER_PORT = 9092
    HDMContext.init(leader = context)
    Thread.sleep(1500)

    val benchmark = new IterationBenchmark(1, 1)
    benchmark.testWeatherLR(data, 12, 3, parallelism)
  }

  @Test
  def testTeraSort():Unit = {
    val context = "akka.tcp://masterSys@127.0.1.1:8999/user/smsMaster"
    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/partial/rankings"
    //    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/micro/uservisits"
    implicit val parallelism = 1
    HDMContext.NETTY_BLOCK_SERVER_PORT = 9092
    HDMContext.init(leader = context)
    Thread.sleep(1500)

    val benchmark = new KVBasedPrimitiveBenchmark(context)
    val hdm = benchmark.testTeraSort(dataPath = data)
    onEvent(hdm, "sample")
    Thread.sleep(15000000)
  }



  @After
  def after() {
    HDMContext.shutdown()
  }

}
