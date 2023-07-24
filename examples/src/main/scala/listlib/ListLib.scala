
package listlib

trait Printable {
  def str: String
}

abstract class SimpleList[+A] extends Printable {
  def ::[B >: A](elem: B): Cons[B] = Cons(elem, this)

  def ++[B >: A](suffix: SimpleList[B]): SimpleList[B]

  def contains[A1 >: A](elem: A1): Boolean

  def size: Int

  def get(i: Int): A

  def head: A
  
  def tail: SimpleList[A]

  def reverse =
    def rec(remaining: SimpleList[A], acc: SimpleList[A]): SimpleList[A] =
      if remaining.size == 0 then acc
      else rec(remaining.tail, remaining.head :: acc)

    rec(tail, head :: Empty())

  def repeat(n: Int): SimpleList[A] =
    this ++ repeat(n-1)

  def last = get(this.size - 1)

  // including i
  def take(len: Int): SimpleList[A] =
    if len == 0 then Empty()
    else head :: tail.take(len - 1)

  def slice(from: Int, len: Int): SimpleList[A] =
    if from == 0 then take(len)
    else tail.slice(from - 1, len)
}

class Empty[A] extends SimpleList[A] {
  override def contains[A1 >: A](elem: A1): Boolean = false

  override def ++[B >: A](suffix: SimpleList[B]): SimpleList[B] = suffix

  override def str: String = "Nil"

  override def size = 0

  override def get(i: Int) = throw Exception()

  override def head = throw Exception()

  override def tail = throw Exception()
}

class Cons[+A](val first: A, val cons: SimpleList[A]) extends SimpleList[A] {
  override def ++[B >: A](suffix: SimpleList[B]): SimpleList[B] =
    Cons(first, cons ++ suffix)

  override def contains[A1 >: A](elem: A1): Boolean =
    first == elem || cons.contains(elem)

  override def str: String = "(" + first + ")" + cons.str

  override def size: Int = 1 + cons.size

  override def get(i: Int) =
    if i == 0 then first
    else cons.get(i - 1)

  override def tail: SimpleList[A] = cons

  override def head: A = first
}

class IntWrap(val c: Int)

object Main {
  def main(args: Array[String]): Unit = {
    val l: Cons[IntWrap] = IntWrap(0) :: IntWrap(1) :: IntWrap(2) :: IntWrap(3) :: Empty()
    val elt = l.get(3)

    def range(from: Int, to: Int): SimpleList[IntWrap] = {
      var build: SimpleList[IntWrap] = Empty()
      var i = from

      while (i < to) {
        build = IntWrap(i) :: build
        i += 1
      }

      build.reverse
    }

    def display(l: SimpleList[IntWrap]) =
      var i = 0;
      while (i < l.size) {
        println(l.get(i).c)
        i += 1
      }

    val rng = range(0, 100)
    display(rng)

    val sl = rng.slice(10, 15)

    display(sl)
  }
}
