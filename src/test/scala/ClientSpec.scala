import org.scalatest.funspec.AnyFunSpec

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

class ClientSpec extends AnyFunSpec {

  describe("A client"){

    def testClient(client: Client) = {
      val serverPort = 4545
      val routesResponses = List(
        RouteResponse("/a", "1 2 3 4"), // http://localhost:4545/a
        RouteResponse("/b", "5 6 7 "),
        RouteResponse("/c", "8 9 10"),
      )
      val expectedResult = 55

      val blockedServer = new BloquedServer(serverPort).withBloquedRoutes(routesResponses)

      blockedServer.start()

      val fresult = Future{client.collectFromRoutes(routesResponses.map(rr => s"http://localhost:$serverPort${rr.route}"))}

      Esperador.esperaSync(2.seconds){ () =>
        blockedServer.getNumConcurrentBloquedRequests() == routesResponses.size
      }

      blockedServer.unblockRoutes()
      val result = Await.result(fresult, 2.seconds)
      assert(result == expectedResult)
      blockedServer.stop()
    }

    it("shoult collect data concurrently"){
      testClient(new ThreadPoolClient)
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
