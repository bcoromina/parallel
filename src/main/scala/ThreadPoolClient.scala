import cats.{Applicative, Monad, Parallel}
import cats.effect.{IO, Sync}

import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent._
import scala.collection.parallel.CollectionConverters._
import scala.collection.parallel.{ExecutionContextTaskSupport, ForkJoinTaskSupport}
import scala.concurrent.ExecutionContext
import scala.util.Try
import cats.implicits._



class ThreadPoolClient[F[_]: Sync]() extends Client[F] with JavaHttpClient{
  override def collectFromRoutes(routes: List[String]) : F[Int] = {
      Sync[F].delay{
        val parallelCollection = routes.par
        parallelCollection.tasksupport = ThreadPoolClient.executionContextTaskSupport
        parallelCollection.flatMap( route => getFromRoute(route).toOption).fold(List.empty[Int])( _ ++ _).sum
      }
  }
}

class IoBlockingClient[F[_]: Sync: Monad: Parallel] extends Client[F] {

  private def getFromRoute(route: String, client: HttpClient): F[List[Int]] = {
    val request = HttpRequest.newBuilder().uri(URI.create(route)).build() // is effectful?
    Sync[F].blocking(client.send(request, BodyHandlers.ofString())).map{ response =>
      response.body().split(" ").toList.map(_.toInt)
    }
  }

  override def collectFromRoutes(routes: List[String]) : F[Int] = {
    for{
      client <- Sync[F].delay(HttpClient.newHttpClient())
      result <-routes.parTraverse(route => getFromRoute(route, client)).map(_.flatten.sum)
    }yield {
      result
    }
  }
}



object NamedThreadFactory {
  private val poolNumber: AtomicInteger = new AtomicInteger(1)
}

class NamedThreadFactory(val poolName: String) extends ThreadFactory {

  private val group: ThreadGroup = Thread.currentThread.getThreadGroup
  private val threadNumber: AtomicInteger = new AtomicInteger(1)
  val poolGroup = NamedThreadFactory.poolNumber.getAndIncrement
  val poolPrefix = poolName + "-" + poolGroup

  def newThread(r: Runnable): Thread = {
    val t: Thread = new Thread(group, r, poolPrefix + "-thread-" + threadNumber.getAndIncrement, 0)
    if (t.isDaemon) t.setDaemon(false)
    if (t.getPriority != Thread.NORM_PRIORITY) t.setPriority(Thread.NORM_PRIORITY)
    t
  }
}


object ThreadPoolClient{
  val forkJoinTaskSupport = new ForkJoinTaskSupport(new ForkJoinPool(5))

  class MyThreadPoolExecutor(poolName: String,
                                       corePoolSize: Int,
                                       maximumPoolSize: Int,
                                       keepAliveTime: Long,
                                       unit: TimeUnit,
                                       workQueue: BlockingQueue[Runnable],
                                      ) extends ThreadPoolExecutor(  corePoolSize,
    maximumPoolSize,
    keepAliveTime,
    unit,
    workQueue,
    new NamedThreadFactory(poolName)
  ) {}

  val executionContextTaskSupport = new ExecutionContextTaskSupport(
    ExecutionContext.fromExecutorService(
      new MyThreadPoolExecutor(
        poolName = "http-client-threadpool",
        corePoolSize = 10,
        maximumPoolSize = 10,
        keepAliveTime = 60L,
        unit = TimeUnit.SECONDS,
        workQueue = new SynchronousQueue[Runnable]
      )
    )
  )
}
