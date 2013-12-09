/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import akka.actor._
import scala.collection.immutable.Queue

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef
    def id: Int
    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection*/
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply
  
  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply

}


class BinaryTreeSet extends Actor {
  import BinaryTreeSet._
  import BinaryTreeNode._
  import context._

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))

  var root = createRoot

  // optional
  var pendingQueue = Queue.empty[Operation]

  // optional
  def receive = normal

  // optional
  /** Accepts `Operation` and `GC` messages. */
  val normal: Receive = {
 	case o : Operation => root ! o
	case GC => {
		

	case _ => {}
  }

  // optional
  /** Handles messages while garbage collection is performed.
    * `newRoot` is the root of the new binary tree where we want to copy
    * all non-removed elements into.
    */
  def garbageCollecting(newRoot: ActorRef): Receive = {
  	case o : Operation => {pendingQueue += o}
	case _ => 
  }

}

object BinaryTreeNode {
  trait Position

  case object Left extends Position
  case object Right extends Position

  case class CopyTo(treeNode: ActorRef)
  case object CopyFinished

  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode],  elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor {
  import BinaryTreeNode._
  import BinaryTreeSet._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved

  // optional
  def receive = normal
  def getPositionFromValue(i : Int) : Position = if(i < elem) Left else Right
  def getChild(i : Int) : Option[ActorRef] = subtrees.get(getPositionFromValue(i))

  // optional
  /** Handles `Operation` messages and `CopyTo` requests. */
  val normal: Receive = {
  	 case  Contains(requester , id, findElem ) => {
	 	if(elem == findElem) requester !  ContainsResult(id , removed)
		else{
			getChild(findElem) match {
				case None => requester ! ContainsResult(id , false)
				case Some(child) => child ! Contains(requester , id , findElem)
			}
		}
	}
	case Insert(requester , id , addElem) => {
		if(addElem == elem) {
			removed = false 
			requester ! OperationFinished(id)
		}
		else
		{
			getChild(addElem) match {
				case None => subtrees = {
					requester ! OperationFinished(id)
					subtrees.+(getPositionFromValue(addElem) -> context.actorOf(BinaryTreeNode.props(addElem , false)))
				}
				case Some(child) => child ! Insert(requester , id , addElem)
			}
		}
	}
	case Remove(requester , id , removeElem) => {
		if(removeElem == elem) {
			removed = true
			requester ! OperationFinished(id)
		}
		else {
			getChild(removeElem) match {
				case None => subtrees = subtrees + (getPositionFromValue(removeElem) -> context.actorOf(BinaryTreeNode.props(removeElem , true)))
				case Some(child) => child ! Remove(requester , id , removeElem)
			}
		}
	}
	case GC => become copying(subtrees.filter(!_.removed , false))
  }

  // optional
  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */
  def copying(expected: Set[ActorRef], insertConfirmed: Boolean): Receive = ???

}

