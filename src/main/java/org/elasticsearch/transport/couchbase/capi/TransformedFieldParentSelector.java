package org.elasticsearch.transport.couchbase.capi;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.elasticsearch.transport.couchbase.CouchbaseCAPIService;

/**
 * Select parent based on transformed field value. Usage:
 *
 * <pre>
 * {@code
 * couchbase.parentSelector: org.elasticsearch.transport.couchbase.capi.TransformedFieldParentSelector
 * couchbase.documentTypeParentFields.childType: doc.parentIdField/(.+)/parentType:$1
 * 
 * "user:${userId}"
 * }
 * </pre>
 *
 * @author tungd on 4/26/15.
 */

public class TransformedFieldParentSelector implements ParentSelector {
    protected Logger logger = Loggers.getLogger(getClass());

    private static final Pattern fieldPattern = Pattern.compile("\\{(.+?)\\}");

    private static final String documentPrefix = "doc";

    private String documentTypeDelimiter;

    private Map<String, String> documentTypeParentFields;

    private Map<String, String> documentTypeParentMap;

    @Override
    public void configure(Settings settings) {
        this.documentTypeParentFields = CouchbaseCAPIService.Config.DOCUMENT_TYPE_PARENT_FIELDS.get(settings).getAsMap();
        this.documentTypeParentMap = new HashMap<>();
        this.documentTypeDelimiter = CouchbaseCAPIService.Config.DOCUMENT_TYPE_DELIMITER.get(settings);

        for (String key : documentTypeParentFields.keySet()) {
            String expression = documentTypeParentFields.get(key); // user:{userId}
            
            this.documentTypeParentMap.put(key, expression); // <"user", "user:{userId}:{id}">
        }
    }

    @Override
    public Object getParent(Map<String, Object> doc, String docId, String type) {
       if (documentTypeParentMap == null || documentTypeParentMap.isEmpty() || documentTypeParentMap.containsKey(type) == false) {
            return null;
        }

        String expression = documentTypeParentMap.get(type);
        
        if (expression == null) {
            logger.warn("[TransformedFieldParentSelector:getParent] No parent regex found for type {}", type);
            return null;
        }

        String[] parts = expression.split(this.documentTypeDelimiter);
        ArrayList<String> transformed = new ArrayList<String>();

        for (int i = 0; i < parts.length; i++) {
            Matcher m = fieldPattern.matcher(parts[i]);

            if (!m.find()) {
                transformed.add(parts[i]);
            } else {
                String field = m.group(1);
                Object fieldValue = ElasticSearchCAPIBehavior.JSONMapPath(doc, documentPrefix + "." + field);

                if (fieldValue == null) {
                    continue;
                }

                transformed.add(fieldValue.toString());
            }
        }

        String parentDocId = String.join(this.documentTypeDelimiter, transformed);

        return parentDocId;
    }
}