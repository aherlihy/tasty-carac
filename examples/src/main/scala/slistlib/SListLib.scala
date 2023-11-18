package slistlib

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
    this ++ repeat(n - 1)

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

class IntWrap(val c: Int):
  override def toString: String = Integer.toString(c)
class StringWrap(val s: String):
  override def toString: String = s

object Main {
  def main(args: Array[String]): Unit = {
    def display(l: SimpleList[IntWrap]) =
      var i = 0;
      while (i < l.size) {
        System.out.println(l.get(i).c)
        i += 1
      }

    def deserialize(input: StringWrap): SimpleList[IntWrap] =
      var build: SimpleList[IntWrap] = Empty()
      val split = input.s.split(",")
      var i = 0

      while (i < split.length) {
        build = IntWrap(java.lang.Integer.parseInt(split(i))) :: build
        i += 1
      }
      build

    def serialize(input: SimpleList[IntWrap]): StringWrap =
      var i = 0;
      var out = ""
      while (i < input.size) {
        out = out + input.get(i) + ","
        i += 1
      }
      StringWrap(out)

    // INPUT_VAR is input variable, which gets serialized and deserialized, and eventually assigned to OUTPUT_VAR
    val INPUT_VAR: Cons[IntWrap] = IntWrap(0) :: IntWrap(1) :: IntWrap(2) :: IntWrap(3) :: Empty()

    System.out.println("input var: ")
    display(INPUT_VAR)

//    val INT_INPUT_1 = INPUT_VAR
//    val INT_INPUT_2 = INT_INPUT_1

//    val SERIALIZED_VAR = serialize(INT_INPUT_2)
    val SERIALIZED_VAR = serialize(INPUT_VAR)

    val INT_SERIALIZED = SERIALIZED_VAR

//    val INT_OUTPUT_1 = deserialize(INT_SERIALIZED)
    val OUTPUT_VAR = deserialize(INT_SERIALIZED)

//    val INT_OUTPUT_2 = INT_OUTPUT_1
//    val OUTPUT_VAR = INT_OUTPUT_2

    val elt = OUTPUT_VAR.get(3)

    def range(from: Int, to: Int): SimpleList[IntWrap] = {
      var build: SimpleList[IntWrap] = Empty()
      var i = from

      while (i < to) {
        build = IntWrap(i) :: build
        i += 1
      }

      build.reverse
    }

    val rng = range(0, 100)
//    display(rng)

    val sl = rng.slice(10, 15)
//    display(sl)

    System.out.println("output var: ")
    display(OUTPUT_VAR)
  }
}
//object Main {
//  def int_from_string(string: String): Int = string.toInt
//  def string_from_int(int: Int): String = int.toString
//  def main(args: Array[String]): Unit = {
//    val input = 1
//    val serialized = string_from_int(input)
//    val desrialized = int_from_string(serialized)
//    return desrialized
//
//    // ActualReturn:
//    //    instr#2	slistlib.Main.main.serialized                       => serialized = instr#2
//    //    instr#3	slistlib.Main.main.desrialized                      => deserialized => instr#3
//    // ActualArg:
//    //    instr#2	list0	arg0	slistlib.Main.main.input                => instr#2(arg0 = input)
//    //    instr#3	list0	arg0	slistlib.Main.main.serialized           => instr#3(arg0 = serialized)
//    // StaticCall:
//    //    slistlib.Main.string_from_int	instr#2	slistlib.Main.main    => instr#2 called from main
//    //    slistlib.Main.int_from_string	instr#3	slistlib.Main.main    => instr#3 called from main
//    // InterProcAssign:
//    //  slistlib.Main.string_from_int.int	    slistlib.Main.main.input            => string_from_int(input)
//    //  slistlib.Main.int_from_string.string	slistlib.Main.main.serialized       => int_from_string(serialized)
//    //  slistlib.Main.main.serialized	        slistlib.Main.string_from_int.temp  => serialized = string_from_int
//    //  slistlib.Main.main.desrialized	      slistlib.Main.int_from_string.temp  => dsrialized = int_from_string
//  }
//}