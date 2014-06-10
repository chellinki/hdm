package com.baidu.bpit.akka.persistence

import junit.framework.TestCase
import com.baidu.bpit.akka.messages.HeartbeatMsg
import scala.collection.JavaConversions._
import com.baidu.bpit.akka.monitor.MonitorData

class DefaultPersistenceTest  extends TestCase{

  val persistenceService = new DefaultPersistenceService(32)
  
  def testGetMonitorData {
    for( i <- 1 to 12;  j <- "thisisatestmessage"){
      val data:List[MonitorData] = List( MonitorData(monitorName="testMonitor", value=j.toString, key=i.toString ,prop="cpu"+i, source = "local"))
      persistenceService.saveMasterMessage(HeartbeatMsg(null, null, data))
    }
    println(persistenceService.getData("cpu12"))
    println(persistenceService.getData("cpu12", "e"))
    println(persistenceService.getData("cpu12", List("e","t")))
  }
  
}