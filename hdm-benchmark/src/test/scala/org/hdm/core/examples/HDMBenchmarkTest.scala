package org.hdm.core.examples

import org.junit.Test
import org.hdm.core.benchmark.MultiClusterBenchmark

/**
 * Created by tiantian on 9/01/15.
 */
class HDMBenchmarkTest {

  val master1 = "akka.tcp://masterSys@127.0.1.1:8999/user/smsMaster"
  val master2 = "akka.tcp://masterSys@127.0.1.1:8998/user/smsMaster"
  val multiClusterBenchmark =  new MultiClusterBenchmark(master1, master2)

  @Test
  def testRunParallelJobs(): Unit = {
    implicit val parallelism = 2
    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/1node/weather"
    multiClusterBenchmark.testParallelExecution(data, data)

    Thread.sleep(100000)
  }

  @Test
  def testRunShuffleJobs(): Unit = {
    implicit val parallelism = 2
    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/micro/rankings"
    multiClusterBenchmark.testShuffleTask(data, data)

    Thread.sleep(1000000)
  }

  @Test
  def testWeatherLR(): Unit ={
    implicit val parallelism = 2
    val data = "hdfs://127.0.0.1:9001/user/spark/benchmark/partial/weather"
    multiClusterBenchmark.testMultiPartyLR(data, data, 12 ,3)

    Thread.sleep(1000000)
  }

}
