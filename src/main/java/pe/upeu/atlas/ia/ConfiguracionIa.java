// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.ia;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.upeu.atlas.config.PropiedadesAtlas;

import java.time.Duration;

/** Modelos de LangChain4j apuntando al Ollama local. */
@Configuration
public class ConfiguracionIa { // K

    @Bean
    public ChatLanguageModel modeloChat(PropiedadesAtlas propiedades) {
        var llm = propiedades.getLlm();
        return OllamaChatModel.builder()
                .baseUrl(llm.getUrl())
                .modelName(llm.getModelo())
                .temperature(llm.getTemperatura())
                .timeout(Duration.ofSeconds(llm.getTiempoEsperaSegundos()))
                .build();
    } // Y

    /** Embeddings (nomic-embed-text) para la búsqueda semántica en documentos. */
    @Bean
    public EmbeddingModel modeloEmbeddings(PropiedadesAtlas propiedades) {
        var llm = propiedades.getLlm(); // K
        return OllamaEmbeddingModel.builder()
                .baseUrl(llm.getUrl())
                .modelName(llm.getModeloEmbeddings())
                .timeout(Duration.ofSeconds(llm.getTiempoEsperaSegundos()))
                .build(); // K
    }
}
