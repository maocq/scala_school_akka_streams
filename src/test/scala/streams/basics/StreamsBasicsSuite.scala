package streams.basics

import akka.actor._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.{Done, NotUsed}
import akka.stream.scaladsl._
import akka.util.Timeout
import org.scalatest.FunSuite

import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

class StreamsBasicsSuite extends FunSuite {
  test("Smoke test"){
    assert(true)
  }

  test("Se puede construir un Source a partir de un Iterable cualquiera"){

    implicit val system = ActorSystem("SystemForTestingAkkaStreams")
    implicit val materializer = ActorMaterializer()

    /*Se puede crear un Source con su apply por defecto que toma un Iterable.
    El NotUsed significa que el Source no se materializa a un valor.
    */
    val source: Source[Int, NotUsed] = Source(1 to 10)
    /*Como estamos probando un Source vamos por ahora a hacer un flujo de datos
    que arranca en source y se va a un Sink que ignora los datos. Debemos entocnes
    crear el sink para luego unirlo con el source.
    */
    val sink: Sink[Any, Future[Done]] = Sink.ignore
    /*Un source se debe unir a un sink para luego poder ejecutarse. Notar como
    el tipo de source.to(sink) es un grafo y que adicionalmente esta parametrizado a NotUsed
    pues se trata simplemente de la descripcion de un flujo y no su ejecucion (aka su materializacion)
    */
    val g: RunnableGraph[NotUsed] = source.to(sink)
    /*Un grafo se materializa con run. Es importante ver que para poder ejecutar un grafo siempre hay que proveer
    ActorSystem y un ActorMaterializer de forma implicita
    */

    val res: NotUsed = g.run()
    /*Este test solo demuestra que es lo minimo necesario para construir un
    grafo que compile. Dado que el Sink es ignore no podemos realizar
    ninguna verificacion
    */
    assert(true)
  }

  test("Se puede construir un Source que materializa a un valor"){

    implicit val system = ActorSystem("SystemForTestingAkkaStreams")
    implicit val materializer = ActorMaterializer()

    /*Se puede crear un Source con su apply por defecto que toma un Iterable.
  El NotUsed significa que el Source no se materializa a un valor.
    */
    val source2: Source[Int, String] = Source.fromGraph(new SourceStageTest())
    /*Como estamos probando un Source vamos por ahora a hacer un flujo de datos
    que arranca en source y se va a un Sink que ignora los datos. Debemos entocnes
    crear el sink para luego unirlo con el source.
    */
    val sink: Sink[Any, Future[Done]] = Sink.foreach(println)
    /*Un source se debe unir a un sink para luego poder ejecutarse. Notar como
    el tipo de source.to(sink) es un grafo y que adicionalmente esta parametrizado a NotUsed
    pues se trata simplemente de la descripcion de un flujo y no su ejecucion (aka su materializacion)
    */
    val g: RunnableGraph[String] = source2.toMat(sink)(Keep.left)
    /*Un grafo se materializa con run. Es importante ver que para poder ejecutar un grafo siempre hay que proveer
    ActorSystem y un ActorMaterializer de forma implicita
    */

    val res = g.run()
    println("MATERIALIZED => " + res)
    /*Este test solo demuestra que es lo minimo necesario para construir un
    grafo que compile. Dado que el Sink es ignore no podemos realizar
    ninguna verificacion

    */

    assert(true)
  }

  test("Unir un source a un sink que haga algo"){
    /*Los implicits siempre necesarios para poder ejecutar un grafo*/
    implicit val system = ActorSystem("SystemForTestingAkkaStreams")
    implicit val materializer = ActorMaterializer()

    val source: Source[Int, NotUsed] = Source(1 to 10)

    /*Esta vez tenemos un Sink que hace algo. Cuando le lleguen datos
    * los va a foldear (acumular) con la operacion binaria + */
    val sink: Sink[Int, Future[Int]] = Sink.fold(0)(_+_)

    /*Notar dos cosas:
    * 1. Que esta vez el to cambiar por toMat
    * 2. Que r1 no tiene el keepRight al final. Por esto, no hay nugrafo para ejecutar*/
    val r1: ((NotUsed, Future[Int]) => Nothing) => RunnableGraph[Nothing] = source.toMat(sink)

    /*r1 si tiene Keep.right y es por eso que evalua a grafo y se puede ejecutar posteriormente*/
    val r2: RunnableGraph[Future[Int]] = source.toMat(sink)(Keep.right)

    /*La ejecucion del grafo es lo que dice la intuicion. Toma el source
    * que son los numeros del 1 al 10 y los lleva a un Sink que los va a acumular
    * y entrega el valor de la suma en un Future. Esto denota que el futuro se completa
    * cuando el stream termine*/
    val resFut1: Future[Int] = r2.run()

    /*Esperamos por el futuro (porque estamos en tests y lo podemos hacer)
    * y verificamos que el valor final sea la suma de los numeros del 1 l 10*/
    val res1 = Await.result(resFut1, Duration.Inf)
    assertDoesNotCompile("r1.run()")
    val valorEsperado = 1+2+3+4+5+6+7+8+9+10
    assert(res1== valorEsperado)

    /*Finalmente vemos que para evitar la sintaxis un poco confusa del Keep.right
    * se puede hacer dos pasos al mismo tiempo:
    * 1. El enlace con sink (que recien lo hicimos con .to)
    * 2. La ejecucion del stream (que recien la hicimos con .run)
    * .runWith hace las dos cosas al tiempo*/
    val resFut2 = source.runWith(sink)

    /*Esperamos resultado y verificamos que de lo mismo que la ejecucion previa*/
    val res2 = Await.result(resFut2, Duration.Inf)
    assert(res2 == valorEsperado)
  }

  test("Un source es inmutable"){
    implicit val system = ActorSystem("SystemForTestingAkkaStreams")
    implicit val materializer = ActorMaterializer()

    val source1: Source[Int, NotUsed] = Source(1 to 10)
    /*Como en collections, esta transformacion de source se pierde
    * pues no se asigna el resultado de la evaluacion a un val nuevo
    * y source sigue siendo el flujo original de 1 a 10*/
    source1.map(x=>0)
    val source2: Source[Int, NotUsed] = source1.map(x => 0)
    val sink: Sink[Int, Future[Int]] = Sink.fold(0)(_+_)

    val valorEsperado = 1+2+3+4+5+6+7+8+9+10

    val resFut1 = source1.runWith(sink)
    val resFut2 = source2.runWith(sink)

    val res1 = Await.result(resFut1, Duration.Inf)
    val res2 = Await.result(resFut2, Duration.Inf)

    assert(res1 == valorEsperado)
    assert(res1 != res2)
    assert(res2 == 0)

  }

  test("Un Source puede ser compuesto con un Flow y siguen siendo Source"){
    implicit val system = ActorSystem("SystemForTestingAkkaStreams")
    implicit val materializer = ActorMaterializer()

    /*El ya conocido Source*/
    val source: Source[Int, NotUsed] = Source(1 to 10)
    /*Esto es nuevo. Es un flow. La semantica de
    * Flow[Int] es: cuando tenga un Int hare con el lo siguiente ...
    * En este caso, ante un Int se entrega otro Int correspondiente al que
    * ingresa incrementado en 1.
    * La semantica de Flow[Int, Int, NotUsed] es: Un flow que dado un Int, entrega un Int
    * y "no importa" lo que suceda con la materializacion (ver la documentacion de NotUsed)*/
    val flow: Flow[Int, Int, NotUsed] = Flow[Int].map(_+1)

    /*A un source se le puede "pegar" un flow y el resultado es un Source pues ellos
    * dos juntos generan una nueva fuente de datos.
    * A un Source se le anaden flujos con el operador via.*/
    val newSource: Source[Int, NotUsed] = source.via(flow)

    val sink: Sink[Int, Future[Int]] = Sink.fold(0)(_+_)
    val resFut = newSource.runWith(sink)
    val res = Await.result(resFut, Duration.Inf)
    val valorEsperado = (1+1)+(2+1)+(3+1)+(4+1)+(5+1)+(6+1)+(7+1)+(8+1)+(9+1)+(10+1)
    assert(res == valorEsperado)
  }

  test("A un Sink le puede anteceder un Flow y sigue siendo Sink"){
    implicit val system = ActorSystem("SystemForTestingAkkaStreams")
    implicit val materializer = ActorMaterializer()

    var t = 0
    val source = Source(1 to 10)
    val sink: Sink[Int, Future[Done]] = Sink.foreach(t+=_)
    val flow: Flow[Int, Int, NotUsed] = Flow[Int].map(_+1)
    /*La unica manera para que un flow pueda ir a sink y quedar en Sink
    * es que el Sink no sea materializado. Por ahora el unico Sink
    * que podemos obtener sin ser materializado es con Sink.foreach
    * pero posteriormente veremos como esto tiene sentido cuando los datos
    * de un stream van a un Actor.*/
    val newSink: Sink[Int, NotUsed] = flow.to(sink)
    val resFut = source.runWith(newSink)
    /*En este caso tampoco podemos verificar nada con nuestro stream mas alla
    * que compile y tenga sentido que FLow + Sink = Sink*/
    assert(true)
  }

  test("Se pueden declarar flujos desde funciones normales"){
    implicit val system = ActorSystem("SystemForTestingAkkaStreams")
    implicit val materializer = ActorMaterializer()
    def plusOne(i:Int):Int = i +1
    def twice(i:Int):Int = i * 2
    val flowPlusOne: Flow[Int, Int, NotUsed] = Flow.fromFunction(plusOne)
    val flowTwice: Flow[Int, Int, NotUsed] = Flow.fromFunction(twice)

    val resFut: Future[Int] = Source(1 to 3)
      .via(flowPlusOne)
      .via(flowTwice)
      .runWith(Sink.fold(0)(_+_))

    val res = Await.result(resFut, Duration.Inf)

    val resultadoEsperado = ((1+1)*2)+((2+1)*2)+((3+1)*2)

    assert(res == resultadoEsperado)

  }

  test("Se puede crear un Source desde un Future"){

    import scala.concurrent.ExecutionContext.Implicits.global
    implicit val system = ActorSystem("SystemForTestingAkkaStreams")
    implicit val materializer = ActorMaterializer()

    def foo(i:Int)=i%2 match {
      case 0 => Future(i)
      case _ => Future.failed(new Exception("I don't like odds"))
    }

    //fromFuture obliga a que la materializacion sea NotUsed
    val source: Source[Int, NotUsed] = Source fromFuture foo(0)

    // este fold puede materializar a Future[Int]
    val s: Sink[Int, Future[Int]] = Sink.fold(0)(_+_)

    //runWith internamente es un toMat(Sink)(Keep.right) el cual conserva la materializacion de la derecha
    val f1: Future[Int] = source.runWith(s)

    // Si se hace a mano el toMat se debe escribir el Keep.right para que conserve la materializacion del Sink que es Future[Int]
    val f2: Future[Int] = source.toMat(s)(Keep.right).run()

    // Esta ejecucion no puede evaluar a Future[Int] pues to(Sink).run conserva la materializacion de la izquierda, la cual debe ser NotUsed pues es la del Source
    val f3: NotUsed = source.to(s).run

    val r1 = Await.result(f1, Duration.Inf)
    val r2 = Await.result(f2, Duration.Inf)

    assert(r1 == r2)
    assert(r1 == 0)

    // Y no puedes hacer nada con f3 porque es un NotUsed jajajajaja

  }


  test("Que pasa cuando un stream falla [No future]"){

    import scala.concurrent.ExecutionContext.Implicits.global

    val decider: Supervision.Decider = {
      case _: ArithmeticException => Supervision.Resume
      case _                      => Supervision.Stop
    }

    implicit val system = ActorSystem("SystemForTestingAkkaStreams")
    implicit val materializer = ActorMaterializer(
      ActorMaterializerSettings(system).withSupervisionStrategy(decider))

    // Este source falla cuando el elemento sea 0.
    // La estrategia de recuperacion Supervision.Resume dice que ante falla haga drop del caso (lo ignore)
    // asi que esa excepcion se ignora y el source continua emitiendo
    val source: Source[Int, NotUsed] = Source(0 to 5).map(100 / _)

    val f: Future[Int] = source
      .runWith(Sink.fold(0)(_ + _))

    val r = Await.result(f, Duration.Inf)

    // 228 == (100/1) + (100/2) + (100/3) + (100/4) + (100/5) ... (100/0) falla y se ignora
    // 228 == 100 + 50 + 33 + 25 + 20
    // 228 == 228
    assert(r == 228)
  }

  test("Que pasa cuando un stream falla [Con future]"){

    import scala.concurrent.ExecutionContext.Implicits.global

    val decider: Supervision.Decider = {
      case e: Exception => Supervision.Stop
    }

    implicit val system = ActorSystem("SystemForTestingAkkaStreams")

    implicit val materializer = ActorMaterializer(
      ActorMaterializerSettings(system).withSupervisionStrategy(decider))

    def foo(i:Int)=i%2 match {
      case 0 => Future(i)
      case _ => Future.failed(new IllegalStateException("I don't like odds"))
    }

    val source = Source.fromFuture(foo(1).recoverWith{
      case e:IllegalStateException => Future(666)
    })

    val f: Future[Int] = source
      .runWith(Sink.fold(0)(_ + _))

    val r = Await.result(f, Duration.Inf)

    /*
    Si este assert pasa es porque hubo recuperacion en el stream con el recoverWith asi que el stream NO
    se detuvo. Esto significa que la estrategia de recuperacion de `decider` no funciona
    si el Source es fromFuture
     */
    assert(r == 666)
  }

  test("Como funciona onComplete"){
    import scala.concurrent.ExecutionContext.Implicits.global

    val decider: Supervision.Decider = {
      case e: Exception => Supervision.Resume
    }

    implicit val system = ActorSystem("SystemForTestingAkkaStreams")

    implicit val materializer = ActorMaterializer(
      ActorMaterializerSettings(system).withSupervisionStrategy(decider))

    val s = Source(1 to 10)
    val sinkOnComplete: Sink[Any, NotUsed] = Sink.onComplete(x=>println("Done!"))
    val r = s.to(sinkOnComplete).run()
  }

  test("Integrating with Actors"){

    import scala.concurrent.ExecutionContext.Implicits.global
    import akka.pattern.ask

    val decider: Supervision.Decider = {
      case e: Exception => Supervision.Resume
    }

    implicit val system = ActorSystem("SystemForTestingAkkaStreams")

    implicit val materializer = ActorMaterializer(
      ActorMaterializerSettings(system).withSupervisionStrategy(decider))


    object RabbitControlManager {
      def props = Props(new RabbitControlManager)
    }

    class RabbitControlManager extends Actor with ActorLogging{
      def receive = {
        case x:String => {
          sender() ! "OK"
          log.debug(s"Enviando a RabbitMQ: $x")
        }
      }
    }

    object MiActor{
      case class Go()
      def props(a:ActorRef) = Props(new MiActor(a))
    }

    import MiActor._

    class MiActor(rabbitControlManager:ActorRef) extends Actor with ActorLogging{


      def source = Source(1 to 10)

      def sink = Sink.onComplete{
        x => context.system.scheduler.scheduleOnce(1 seconds, self, Go() )
      }

      def flow = Flow[Int].map(i => s"Elemento: $i")

      implicit val askTimeout = Timeout(5.seconds)

      def construirGrafo = source
          .via(flow)
          .mapAsync(parallelism=100)(elem => (rabbitControlManager ? elem).mapTo[String])
          .to(sink)

      def receive ={
        case Go() => {
          log.debug(s"Go recibido!")
          construirGrafo.run()
        }

      }

    }

    val rabbitControl = system.actorOf(RabbitControlManager.props, "rabbitControl")
    val miActor = system.actorOf(MiActor.props(rabbitControl), "miActor")

    miActor ! Go()

  }

}
