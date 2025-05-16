package it.doqui.libra.librabl.business.service.interfaces;

import it.doqui.libra.librabl.views.node.ContentRef;
import it.doqui.libra.librabl.views.node.ContentRequest;
import it.doqui.libra.librabl.views.node.LinkedInputNodeRequest;
import it.doqui.libra.librabl.views.renditions.RenditionNode;
import it.doqui.libra.librabl.views.renditions.RenditionSettings;
import it.doqui.libra.librabl.views.renditions.TransformerNode;
import it.doqui.libra.librabl.views.renditions.TransformerIdentifiedInputRequest;

import java.util.List;
import java.util.Optional;

public interface RenditionService {
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
