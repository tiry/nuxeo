"savedsearch" : {
  "id": "savedsearch",
  "type": "any",
  "required": false,
  "uniqueItems": false,
  "properties": {
      "entity-type": {
          "uniqueItems": false,
          "type": "string",
          "required": true
      },
      "id": {
          "uniqueItems": false,
          "type": "string",
          "required": false
      },
      "title": {
          "uniqueItems": false,
          "type": "string",
          "required": true
      },
      "searchType": {
          "uniqueItems": false,
          "type": "string",
          "required": true
      },
      "langOrProviderName": {
          "uniqueItems": false,
          "type": "string",
          "required": true
      },
      "params": {
          "$ref":"params"
      }
  }
},

"savedsearches" : {
  "id": "savedsearches",
  "uniqueItems": false,
  "properties": {
    "entity-type": {
      "uniqueItems": false,
      "type": "string",
      "required": true
    },
    <#include "views/doc/datatypes/paginable.ftl"/>,
    "entries": {
      "uniqueItems": false,
      "type": "array",
      "items": {
        "$ref":"savedsearch"
      },
      "required": true
    }
  }
},

"params" : {
  "id": "params",
  "required": false,
  "uniqueItems": false,
  "properties": {
    "query": {
        "uniqueItems": false,
        "type": "string",
        "required": false
    },
    "pageSize": {
        "uniqueItems": false,
        "type": "string",
        "required": false
    },
    "currentPageIndex": {
        "uniqueItems": false,
        "type": "string",
        "required": false
    },
    "maxResults": {
        "uniqueItems": false,
        "type": "string",
        "required": false
    },
    "sortBy": {
        "uniqueItems": false,
        "type": "string",
        "required": false
    },
    "sortOrder": {
        "uniqueItems": false,
        "type": "string",
        "required": false
    },
    "queryParams": {
        "uniqueItems": false,
        "type": "object",
        "required": false
    }
  }
}
