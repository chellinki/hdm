package org.nicta.wdy.hdm.functions

import scala.collection.mutable.Buffer

/**
 * Created by tiantian on 2/03/15.
 */

/**
 *
 * @tparam R return type
 */
trait SerializableFunction[I,R] extends  Serializable{

  def apply(params: I) : R

}

trait Aggregatable[T,R] extends  Serializable {


  def aggregate(params:T, res:R): R


}

trait ShuffleAggregatable[K, T, R] extends Aggregatable[T,R]{

  def zero(params:T): R

  def map(param:T): K

}

trait Mergeable[R]{

  def merge(params:Seq[R], res:Buffer[R]): Buffer[R]

}
