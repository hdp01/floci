package io.github.hectorvent.floci.core.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.services.codeartifact.CodeArtifactService;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Dispatcher for the AWS services that put their tag endpoints on {@code /v1/tags/{resourceArn}}
 * (AppSync, MSK).
 *
 * <p>Same problem and same resolution as {@link SharedTagsController}, one path up: AWS tells
 * these services apart by hostname, floci serves them all on one port, so the owning service is
 * resolved from the {@code service} segment of the request ARN and the request handed to the
 * matching {@link TagHandler}. Handlers opt in to this path with {@link V1Tags}.
 *
 * <p>CodeArtifact also lands on this literal prefix, but with {@code resourceArn} as a query
 * parameter on the bare path ({@code POST /v1/tags?resourceArn=...}) rather than a path segment,
 * and with lowercase {@code key}/{@code value} tag members that don't match this class's shared
 * {@link TagDispatcher} list-shape casing, so its one bare-path method is handled directly here
 * instead of going through the {@link TagHandler} machinery built for the path-segment form.
 */
@Path("/v1/tags")
@Produces(MediaType.APPLICATION_JSON)
public class V1TagsController extends AbstractTagsController {

    private final CodeArtifactService codeArtifactService;
    private final ObjectMapper objectMapper;

    @Inject
    public V1TagsController(@V1Tags Instance<TagHandler> handlers,
                            RegionResolver regionResolver,
                            ObjectMapper objectMapper,
                            CodeArtifactService codeArtifactService) {
        super(new TagDispatcher(handlers, regionResolver, objectMapper));
        this.codeArtifactService = codeArtifactService;
        this.objectMapper = objectMapper;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response listTagsForResourceByQuery(@QueryParam("resourceArn") String resourceArn) {
        ObjectNode response = objectMapper.createObjectNode();
        ArrayNode items = response.putArray("tags");
        codeArtifactService.listTagsForResource(resourceArn).forEach((k, v) -> {
            ObjectNode tag = objectMapper.createObjectNode();
            tag.put("key", k);
            tag.put("value", v);
            items.add(tag);
        });
        return Response.ok(response).build();
    }
}
