import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent._
import scala.collection.parallel.CollectionConverters._
import scala.collection.parallel.{ExecutionContextTaskSupport, ForkJoinTaskSupport}
import scala.concurrent.ExecutionContext



class ThreadPoolClient() extends Client with JavaHttpClient{
  override def collectFromRoutes(routes: List[String]) : Int = {
    val parallelCollection = routes.par
    parallelCollection.tasksupport = ThreadPoolClient.executionContextTaskSupport
    parallelCollection.flatMap( route => getFromRoute(route).toOption).fold(List.empty[Int])( _ ++ _).sum
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
