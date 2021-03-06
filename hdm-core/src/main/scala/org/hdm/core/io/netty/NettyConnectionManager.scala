package org.hdm.core.io.netty

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.util.internal.PlatformDependent
import org.hdm.core.context.HDMContext
import org.hdm.core.io.CompressionCodec
import org.hdm.core.serializer.SerializerInstance
import org.hdm.core.utils.Logging

import scala.collection.JavaConversions._

/**
 * Created by tiantian on 31/05/15.
 */
class NettyConnectionManager(val connectionNumPerPeer:Int,
                             val threadsPerCon:Int,
                             val serializerInstance: SerializerInstance,
                             val compressor:CompressionCodec) {

//  val connectionNumPerPeer = HDMContext.NETTY_CLIENT_CONNECTIONS_PER_PEER

  private val connectionCacheMap = new ConcurrentHashMap[String, ConnectionPool]()


/*
   def getConnection(host:String, port:Int):NettyBlockFetcher ={
    val cId = host + ":" + port
    val activeCon = if(connectionCacheMap.containsKey(cId)) {
      val con = connectionCacheMap.get(cId)
      if(con.isConnected()) con
      else {
        con.shutdown()
        connectionCacheMap.remove(cId)
        NettyConnectionManager.createConnection(host, port)
      }
    } else {
      NettyConnectionManager.createConnection(host, port)
    }
    connectionCacheMap.put(cId, activeCon)
    if(!activeCon.isRunning) activeCon.schedule()
    activeCon
  }
  */

  def getConnection(host:String, port:Int):NettyBlockFetcher ={
    val cId = host + ":" + port
    if(!connectionCacheMap.containsKey(cId)) {
      connectionCacheMap.put(cId, new ConnectionPool(connectionNumPerPeer, host, port, threadsPerCon, serializerInstance, compressor))
    }
    val activeCon = connectionCacheMap.get(cId).getNext()
    if(!activeCon.isRunning) activeCon.schedule()
    activeCon
  }

  def recycleConnection(host:String, port:Int, con: NettyBlockFetcher) = {
    val cId = host + ":" + port
    if(!connectionCacheMap.contains(cId)){
      connectionCacheMap.put(cId, new ConnectionPool(connectionNumPerPeer,host, port, threadsPerCon, serializerInstance, compressor))
    }
    connectionCacheMap.get(cId).recycle(con)
  }

  def clear(): Unit ={
    connectionCacheMap.values().foreach(_.dispose())
    connectionCacheMap.clear()
  }

}

object NettyConnectionManager extends Logging{

  lazy val defaultManager = {
    val compressor = if (HDMContext.BLOCK_COMPRESS_IN_TRANSPORTATION) HDMContext.DEFAULT_COMPRESSOR
    else null
    new NettyConnectionManager(HDMContext.NETTY_CLIENT_CONNECTIONS_PER_PEER,
      HDMContext.NETTY_BLOCK_CLIENT_THREADS,
      HDMContext.DEFAULT_SERIALIZER, compressor)
  }

  val localHost = InetAddress.getLocalHost.getHostName

  def createPooledByteBufAllocator(allowDirectBuffers:Boolean, cores:Int) = {
    val heapArena = cores
    val directArena = if(allowDirectBuffers) cores else 0
    val pageSize = 8192
    val maxOrder = 11
    val tinySize = if(allowDirectBuffers) 512 else 0
    val smallSize = if(allowDirectBuffers) 256 else 0
    val normal = if(allowDirectBuffers) 64 else 0
    new PooledByteBufAllocator(allowDirectBuffers && PlatformDependent.directBufferPreferred(),
      heapArena,
      directArena,
      pageSize,
      maxOrder,
      tinySize, smallSize, normal
    )
  }

  def getFrameDecoder(): ChannelInboundHandlerAdapter = {
    new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, -4, 4)
  }

/*
  def getFrameEncoder():MessageToByteEncoder = {
    new LengthFieldPrepender(8)
  }
  */

  def getPrivateStaticField(name:String) = try {
    val f = PooledByteBufAllocator.DEFAULT.getClass().getDeclaredField(name)
    f.setAccessible(true);
    f.getInt(null);
  }

  def getInstance = defaultManager

  def createConnection(host:String,
                       port:Int,
                       nThreads:Int,
                       serializerInstance: SerializerInstance,
                       compressor:CompressionCodec) = {
    log.info(s"creating a new connection for $host:$port")
    val blockFetcher = new NettyBlockFetcher(nThreads, serializerInstance, compressor)
    blockFetcher.init()
    blockFetcher.connect(host, port)
    blockFetcher
  }

}
