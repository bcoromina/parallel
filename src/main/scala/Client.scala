import java.net.URI
import java.net.http.{HttpClient, HttpRequest}
import java.net.http.HttpResponse.BodyHandlers
import scala.util.Try

trait Client[F[_]] {
  def collectFromRoutes(routes: List[String]) : F[Int]
}


trait JavaHttpClient{
  val client = HttpClient.newHttpClient()
  protected def getFromRoute(route: String): Try[List[Int]] = Try{
    val request = HttpRequest.newBuilder()
      .uri(URI.create(route))
      .build()
    val response = client.send(request, BodyHandlers.ofString())
    response.body().split(" ").toList.map(_.toInt)
  }
}
