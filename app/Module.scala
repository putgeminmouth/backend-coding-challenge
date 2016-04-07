import com.google.inject.AbstractModule
import dao.{PostgresSuggestionDao, SuggestionDao}

class Module extends AbstractModule {
    def configure() = {
        bind(classOf[SuggestionDao])
            .to(classOf[PostgresSuggestionDao])
    }
}
