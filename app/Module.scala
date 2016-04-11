import com.google.inject.AbstractModule
import dao.{PrefixPostgresSuggestionDao, SuggestionDao}
import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module}

class MyModule extends Module {
    override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = {
        def getString(name: String) = configuration.getString(name).getOrElse(sys.error(s"missing config: $name"))
        def getInt(name: String)    = configuration.getInt(name).getOrElse(sys.error(s"missing config: $name"))
        def getDouble(name: String)    = configuration.getDouble(name).getOrElse(sys.error(s"missing config: $name"))
        def getClass[T](name: String) = Class.forName(getString(name)).asInstanceOf[Class[T]]

        def bindInt(name: String)    = bind[Int].qualifiedWith(name).to(getInt(name))
        def bindDouble(name: String) = bind[Double].qualifiedWith(name).to(getDouble(name))
        Seq(
            bind[SuggestionDao]
                .to(getClass[SuggestionDao]("dao.impl")),
            bindInt("dao.hardlimit"),
            bindDouble("similarity.nameWeight"),
            bindDouble("similarity.distanceWeight"),
            bindDouble("similarity.distanceScale")
        )
    }
}
