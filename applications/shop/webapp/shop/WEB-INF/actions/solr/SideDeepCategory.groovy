/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


import org.ofbiz.base.util.*;
import org.ofbiz.product.catalog.*;
import org.ofbiz.product.category.*;
import javolution.util.FastMap;
import javolution.util.FastList;
import com.ilscipio.solr.SolrUtil;
import org.apache.solr.client.solrj.*;
import org.apache.solr.client.solrj.response.*;
import org.apache.commons.lang.StringUtils;

currentTrail = org.ofbiz.product.category.CategoryWorker.getTrailNoTop(request);

currentCatalogId = CatalogWorker.getCurrentCatalogId(request);
curCategoryId = parameters.category_id ?: parameters.CATEGORY_ID ?: parameters.productCategoryId ?: "";
curProductId = parameters.product_id ?: "" ?: parameters.PRODUCT_ID ?: "";    
topCategoryId = CatalogWorker.getCatalogTopCategoryId(request, currentCatalogId);

catLevel = null; // use null here, not empty list
if (curCategoryId) {
    // Cato: FIXME?: Currently something wrong with this query, returns bad categories (currently filtered by FTL)
    res = dispatcher.runSync("solrSideDeepCategory",[productCategoryId:curCategoryId, catalogId:currentCatalogId, currentTrail:currentTrail]);
    catLevel = res.get("categories");
}

// Cato: promo category (added for testing purposes; uncomment line below to remove again)
promoCategoryId = CatalogWorker.getCatalogPromotionsCategoryId(request, currentCatalogId);
//promoCategoryId = null;

// Cato: best-sell category (added for testing purposes; uncomment line below to remove again)
bestSellCategoryId = CatalogWorker.getCatalogBestSellCategoryId(request, currentCatalogId);
//bestSellCategoryId = null;

//Debug.logInfo("catList "+catLevel,"");
currentCategoryPath = null;
if (curCategoryId) {
    currentCategoryPath = com.ilscipio.solr.CategoryUtil.getCategoryNameWithTrail(curCategoryId, false, 
        dispatcher.getDispatchContext(), currentTrail);
}
context.currentCategoryPath = currentCategoryPath;
context.catList = catLevel;
topLevelList = [topCategoryId];
if (promoCategoryId) {
    // Cato: Adding best-sell to top-levels for testing
    topLevelList.add(promoCategoryId);
}
if (bestSellCategoryId) {
    topLevelList.add(bestSellCategoryId);
}
context.topLevelList = topLevelList;
context.curCategoryId = curCategoryId;
context.topCategoryId = topCategoryId;

context.promoCategoryId = promoCategoryId;
context.bestSellCategoryId = bestSellCategoryId;

// Cato: if multiple top categories, need to record the current base category
if (topLevelList.size() >= 2) {
    baseCategoryId = topCategoryId; // default if somehow none found
    if (currentCategoryPath) {
        currentPathRoot = currentCategoryPath.split("/")[0];
        for(catId in topLevelList) {
            if (catId == currentPathRoot) {
                baseCategoryId = catId;
                break;
            }
        }
    }
}
else if (topLevelList.size() >= 1) {
    baseCategoryId = topLevelList[0];
}
else {
    baseCategoryId = null;
}
context.baseCategoryId = baseCategoryId;



