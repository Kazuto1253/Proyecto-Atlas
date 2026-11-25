// Autores: K (Kevin) y Y (Yenifher)
package pe.upeu.atlas.ia;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pe.upeu.atlas.config.SanchezPropiedadesAtlas;

import java.time.Duration;

/** Modelos de LangChain4j apuntando al Ollama local. */
@Configuration
public class SanchezConfiguracionIa { // K

    @Bean
    public ChatLanguageModel marcamodeloChat(SanchezPropiedadesAtlas marcapropiedades) {
        var sanchezllm = marcapropiedades.getLlm();
        return OllamaChatModel.builder()
                .baseUrl(sanchezllm.getUrl())
                .modelName(sanchezllm.getModelo())
                .temperature(sanchezllm.getTemperatura())
                .timeout(Duration.ofSeconds(sanchezllm.getTiempoEsperaSegundos()))
                .build();
    } // Y

    /** Embeddings (nomic-embed-text) para la búsqueda semántica en documentos. */
    @Bean
    public EmbeddingModel marcamodeloEmbeddings(SanchezPropiedadesAtlas marcapropiedades) {
        var sanchezllm = marcapropiedades.getLlm(); // K
        return OllamaEmbeddingModel.builder()
                .baseUrl(sanchezllm.getUrl())
                .modelName(sanchezllm.getModeloEmbeddings())
                .timeout(Duration.ofSeconds(sanchezllm.getTiempoEsperaSegundos()))
                .build(); // K
    }
}
