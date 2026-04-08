package it.doqui.libra.librabl.application.ports.in;

import it.doqui.libra.librabl.domain.model.files.ContentRef;
import it.doqui.libra.librabl.application.model.graph.ContentRequest;
import it.doqui.libra.librabl.application.model.graph.LinkedInputNodeRequest;
import it.doqui.libra.librabl.application.model.rendition.RenditionNode;
import it.doqui.libra.librabl.application.model.rendition.RenditionSettings;
import it.doqui.libra.librabl.application.model.rendition.TransformerNode;
import it.doqui.libra.librabl.application.model.rendition.TransformerIdentifiedInputRequest;

import java.util.List;
import java.util.Optional;

public interface RenditionUseCase {
    List<TransformerNode> findRenditionTransformers(ContentRequest cr);
    Optional<TransformerNode> getRenditionTransformer(ContentRequest cr);
    List<RenditionNode> findRenditionNodes(ContentRequest xmlRef, ContentRequest transformerRef, Boolean generated, boolean oldModeEnabled);
    TransformerNode createAndAssignTransformer(ContentRequest cr, TransformerIdentifiedInputRequest renditionRequest);
    void deleteTransformer(ContentRequest xmlContentRequest, ContentRequest rtContentRequest);
    RenditionNode setNodeRendition(ContentRequest xml, ContentRequest rt, LinkedInputNodeRequest input);
    RenditionNode generateRendition(ContentRef renditionableRequest, ContentRef transformerRequest, RenditionSettings renditionSettings);
    void deleteRendition(ContentRequest xmlContentRequest, ContentRequest rtContentRequest, ContentRequest rdContentRequest);
    void deleteRenditions(ContentRequest xmlContentRequest, ContentRequest rtContentRequest, Boolean generated);
}
