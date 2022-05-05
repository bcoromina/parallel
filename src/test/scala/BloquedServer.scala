
import org.eclipse.jetty.server.{Server, ServerConnector}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}

import java.util.concurrent.atomic.AtomicInteger
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Promise}
import org.apache.http.HttpStatus

import scala.util.Try

case class RouteResponse(route: String, response: String)

class BloquedServer(port: Int) {

  private val lockedRoutes = Promise[Unit]()
  private val numConcurrentRequests =  new AtomicInteger(0)
  private val server = new Server(port)

  private val context = new ServletContextHandler()
  context.setContextPath("/")
  server.setHandler(context)

  def getNumConcurrentBloquedRequests(): Int = numConcurrentRequests.get()
  def unblockRoutes(): Unit = lockedRoutes.tryComplete(Try())
  def waitForUnblockRoutes(): Unit = Await.result(lockedRoutes.future, 1.minute)

  def withBloquedRoutes(routesResponses: List[RouteResponse]): BloquedServer = {
    routesResponses.foreach{ rr =>
      this.addRouteWithPath(
        (req, resp) => {
          assert(req.getMethod == "GET")
          val writer = resp.getWriter
          writer.write(rr.response)
          this.numConcurrentRequests.incrementAndGet()
          this.waitForUnblockRoutes()
          resp.setStatus(HttpStatus.SC_OK)
        }
        , rr.route)
    }
    this
  }

  class WebServlet(handler: (HttpServletRequest, HttpServletResponse) => Unit) extends HttpServlet {
    override def service(req: HttpServletRequest, resp: HttpServletResponse): Unit = {
      handler(req, resp)
    }
  }

  def addRouteWithPath(callback: (HttpServletRequest, HttpServletResponse) => Unit, path: String): String = {
    context.addServlet(
      new ServletHolder(new WebServlet(callback)),
      path,
    )
    url(path)
  }
  private def baseUrl(): String = {
    server
      .getConnectors
      .collectFirst {
        case conn: ServerConnector =>
          val host = Option(conn.getHost).getOrElse("localhost")
          val p = conn.getLocalPort
          s"http://${host}:${p}"
      }
      .getOrElse(
        throw new IllegalStateException("No se ha podido calcular la URL del servidor de test"),
      )

  }

  private def url(uri: String): String = s"$baseUrl$uri"


  def start(): Unit = {
    server.start()
  }

  def stop(): Unit = {
    server.stop()
  }

}
