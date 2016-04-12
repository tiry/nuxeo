/*
 * @param context An object containing service request context information such as input document types and URIs, and
 *    output types accepted by the caller.
 * @param params An object containing extension-specific parameter values supplied by the client, if any. In our case
 *    the document id.
 * @param input The data from the request body. The update to apply to document.
 */
function put(context, params, input) {
  // Validate input
  if (input instanceof ValueIterator) {
    returnErrToClient(400, 'Bad Request', 'Only one state is allowed in updater.');
  }
  // Validate parameter
  var uri = params.uri;
  if (uri == null) {
    returnErrToClient(400, 'Bad Request', 'Document uri to patch is missing.');
  }

  var document = cts.doc(uri).toObject();
  patchDocument(document, input.toObject());
  xdmp.documentInsert(uri, document);

  context.outputTypes = ['application/json'];
  return {};
}

function returnErrToClient(statusCode, statusMsg, body) {
  fn.error(null, 'RESTAPI-SRVEXERR',
    xdmp.arrayValues([statusCode, statusMsg, body]));
  // unreachable - control does not return from fn.error.
}

function patchDocument(document, patch) {
  for (var key in patch) {
    if (patch[key] === null) {
      // Remove the node from document
      delete document[key];
    } else if (typeof patch[key] === 'object') {
      if (typeof document[key] === 'undefined') {
        // Object doesn't exist in document
        document[key] = patch[key];
      } else {
        var subDocument = document[key];
        var subPatch = patch[key];
        if (subPatch.hasOwnProperty("diff") && subPatch.hasOwnProperty("rpush")) {
          // List diff case
          var iDoc = 0;
          for (var iDiff in subPatch['diff']) {
            var diff = subPatch['diff'][iDiff];
            if (diff === null) {
              subDocument.splice(iDoc, 1);
            } else if (diff === 'NOP') {
              // Do nothing
              iDoc++;
            } else {
              if (typeof diff === 'object') {
                // Loop on sub structure
                patchDocument(subDocument[iDoc], diff);
              } else {
                // Primitive value
                subDocument[iDoc] = diff;
              }
              iDoc++;
            }
          }
          for (var rpush in subPatch['rpush']) {
            subDocument.push(rpush);
          }
        } else {
          // Loop on sub structure
          patchDocument(subDocument, subPatch);
        }
      }
    } else {
      // Primitive value
      document[key] = patch[key];
    }
  }
}

exports.PUT = put;
