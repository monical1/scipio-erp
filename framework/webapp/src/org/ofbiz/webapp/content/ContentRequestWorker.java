package org.ofbiz.webapp.content;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilCodec;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.webapp.taglib.ContentUrlTag;

/**
 * SCIPIO: new class for content request-related implementations.
 */
public abstract class ContentRequestWorker {
    
    public static final String module = ContentRequestWorker.class.getName();
    
    // SCIPIO: FIXME: this is for strict=false mode, and it's a wreck of a workaround
    private static final String htmlEscapedFrontSlash = UtilCodec.encode("html", "/");
    private static final String jsEscapedFrontSlash = "\\/";
    
    /**
     * SCIPIO: builds a content link.
     * <p>
     * SCIPIO: added a urlDecode boolean and changed the default behavior to NOT url-decode (FALSE);
     * it should be done before storing in the database - if/when needed.
     * having default as true would be dangerous!
     * <p>
     * @param ctxPrefix a custom prefix for the URL, that may replace the system-wide default
     * @param strict FALSE by default (for legacy reasons), affects pre-escaped value handling
     */
    public static String makeContentLink(HttpServletRequest request, HttpServletResponse response, String uri, String imgSize, String webSiteId, String ctxPrefix, Boolean urlDecode, Boolean strict) {
        String requestUrl = uri;

        // SCIPIO: Our default behavior is NOT to decode unless requested, in contrast to stock Ofbiz
        if (Boolean.TRUE.equals(urlDecode)) {
            requestUrl = UtilCodec.getUrlDecoder().decode(requestUrl);
        }
        
        if (strict == null) { // SCIPIO: forced to use strict false by default 
            strict = Boolean.FALSE;
        }

        if (strict) {
            if (UtilHttp.isFullUrl(requestUrl)) {
                return requestUrl;
            }
        } else {
            // If the URL starts with http(s) then there is nothing for us to do here
            // SCIPIO: FIXME?: We try to use a better test here, but we are forced to use the Permissive
            // version because - due to Ofbiz design of escaping - here we may receive a uri encoded 
            // in any language - highly problematic!
            if (UtilHttp.isFullUrlPerm(requestUrl)) {
                return requestUrl;
            }
        }
        
        // SCIPIO: we support an extra ctxPrefix, must be done in this function so that the prior checks are done properly
        if (UtilValidate.isNotEmpty(ctxPrefix)) {
            requestUrl = ctxPrefix + getUriPathToConcat(ctxPrefix, requestUrl, strict);
            
            // re-check the URL; if it's full, must return now
            if (strict) {
                if (UtilHttp.isFullUrl(requestUrl)) {
                    return requestUrl;
                }
            } else {
                if (UtilHttp.isFullUrlPerm(requestUrl)) { // NOTE: see warning above
                    return requestUrl;
                }
            }
        }

        // make the link
        StringBuilder newURL = new StringBuilder();
        ContentUrlTag.appendContentPrefix(request, newURL, webSiteId);
        // SCIPIO: handled better below
        //if ((newURL.length() > 0 && newURL.charAt(newURL.length() - 1) != '/') 
        //        && (requestUrl.length()> 0 && requestUrl.charAt(0) != '/')) {
        //    newURL.append('/');
        //}

        if(UtilValidate.isNotEmpty(imgSize)){
            // SCIPIO: WARN/FIXME?: This hardcoded value check does NOT work properly even if it's
            // unhardcoded, because the uri may have been passed encoded in html or some other language.
            // It generally only works if the value is hardcoded straight into FTL, e.g.,
            // <#assign someUrl = "/images/defaultImage.jpg">
            // <@ofbizContentUrl>${someUrl}</@ofbizContentUrl>
            // In Scipio we should probably just avoid having such exceptions, but leaving this
            // here for now for legacy compatibility with old templates.
            if(!"/images/defaultImage.jpg".equals(requestUrl)){
                int index = requestUrl.lastIndexOf(".");
                if (index > 0) {
                    String suffix = requestUrl.substring(index);
                    String imgName = requestUrl.substring(0, index);
                    requestUrl = imgName + "-" + imgSize + suffix;
                }
            }
        }

        newURL.append(getUriPathToConcat(newURL.toString(), requestUrl, strict)); // SCIPIO: getUriPathToConcat
        
        return newURL.toString();
    }
    
    // SCIPIO: WARN/FIXME: does not handle pre-escaped/pre-encoded strings!!!
    private static String getUriPathToConcat(String prefix, String suffix, boolean strict) {
        if (UtilValidate.isEmpty(prefix)) { // this also prevents some security issues (JS)
            return suffix;
        } else if (UtilValidate.isEmpty(suffix)) {
            return "";
        }
        if (strict) {
            if (prefix.endsWith("/")) {
                if (suffix.startsWith("/")) {
                    return suffix.substring(1);
                } else {
                    return suffix;
                }
            } else {
                if (suffix.startsWith("/")) {
                    return suffix;
                } else {
                    // WARN: this could potentially have been a security risk in JS strings in general case (see Freemarker docs),
                    // but in this limited case of content urls, as long as first is non-empty, it will not be.
                    return "/" + suffix;
                }
            }
        } else {
            // FIXME?: This is highly imprecise and heuristic. based on UtilCodec + Freemarker Builtins.
            if (prefix.endsWith("/") || prefix.endsWith(htmlEscapedFrontSlash)) {
                if (suffix.startsWith("/")) {
                    return suffix.substring(1);
                } else if (suffix.startsWith(htmlEscapedFrontSlash)) {
                    return suffix.substring(htmlEscapedFrontSlash.length());
                } else if (suffix.startsWith(jsEscapedFrontSlash)) {
                    return suffix.substring(jsEscapedFrontSlash.length());
                } else {
                    return suffix;
                }
            } else {
                if (suffix.startsWith("/") || suffix.startsWith(htmlEscapedFrontSlash)) {
                    return suffix;
                } else if (suffix.startsWith(jsEscapedFrontSlash)) {
                    // SPECIAL CASE: we can be fairly certain this is a JS-escaped string (at least in part). 
                    // Here we follow Freemarker escaping; as long as the previous char isn't "<", we
                    // can turn it into a regular slash.
                    if (prefix.endsWith("<")) {
                        return suffix;
                    } else {
                        return "/" + suffix.substring(jsEscapedFrontSlash.length());
                    }
                } else {
                    if (prefix.endsWith("<")) {
                        Debug.logWarning("makeContentLink: forced to combine URL parts in non-strict mode using an added forward slash (/), but " +
                                "the prefix ends with a raw less-than character (<); if this is a Javascript string (this method cannot tell), " +
                                "the result may be insecure (see Freemarker documentation for ?js_string built-in). Consider rewriting " +
                                "calls to use strict mode and post-macro escaping instead. prefix/suffix: [" + prefix + "/" + suffix + "]", module);
                    }
                    return "/" + suffix;
                }
            }
        }
    }
    
    public static String makeContentLink(HttpServletRequest request, HttpServletResponse response, String uri, String imgSize, String webSiteId) {
        return makeContentLink(request, response, uri, imgSize, webSiteId, null, null, null);
    }
    
    public static String makeContentLink(HttpServletRequest request, HttpServletResponse response, String uri, String imgSize) {
        return makeContentLink(request, response, uri, imgSize, null, null, null, null);
    }

}
