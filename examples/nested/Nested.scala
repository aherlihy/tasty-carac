package nested

class A()

object Main {
  def main() = {
    def fun() = {
      def fun() = A()
      fun()
    }

    val a = fun()

    {
      def fun() = A()
      val a = fun()
    }

    val b = fun()
  }
}

