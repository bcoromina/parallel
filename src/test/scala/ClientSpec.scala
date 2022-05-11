import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.funspec.AnyFunSpec

import java.util.concurrent.Executors
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.Try


class ClientSpec extends AnyFunSpec {


  describe("A client"){



    def testClient(client: Client[IO]) = {
      val serverPort = 4545
      val routesResponses = List(
        RouteResponse("/a", "1 2 3 4"), // http://localhost:4545/a
        RouteResponse("/b", "5 6 7 "),
        RouteResponse("/c", "8 9 10"),
      )
      val expectedResult = 55

      val blockedServer = new BloquedServer(serverPort).withBloquedRoutes(routesResponses)

      blockedServer.start()

      val fresult = client.collectFromRoutes(routesResponses.map(rr => s"http://localhost:$serverPort${rr.route}")).unsafeToFuture()

      Esperador.esperaSync(2.seconds){ () =>
        blockedServer.getNumConcurrentBloquedRequests() == routesResponses.size
      }

      blockedServer.unblockRoutes()
      val result = Await.result(fresult, 2.seconds)
      assert(result == expectedResult)
      blockedServer.stop()

    }


    it("par sequence"){
      testClient(new ThreadPoolClient)
    }

    it(" IO.blocking client"){
      testClient(new IoBlockingClient)
    }


  }

}

object Esperador {


  val context: ExecutionContext = ExecutionContext.fromExecutor(
    Executors.newCachedThreadPool(
      new NamedThreadFactory("esperador"),
    ),
  )

  val currentThreadContext = ExecutionContext.fromExecutor((runnable: Runnable) => {
    runnable.run()
  })

  val predicateEvaluationInterval: Duration = 200.millis

  def espera(predicate: () => Boolean)(callback: => Unit): Unit = {
    context.execute(() => {
      while (!predicate()) {
        Thread.sleep(predicateEvaluationInterval.toMillis)
      }
      callback
    })
  }

  def esperaSync(timeout: FiniteDuration)(predicate: () => Boolean): Unit = {
    Await.result(esperaFuture(predicate), timeout)
  }

  def esperaFuture(predicate: () => Boolean): Future[Unit] = {
    val p = Promise[Unit]()
    espera(predicate) {
      p.complete(Try(()))
    }
    p.future
  }

}
