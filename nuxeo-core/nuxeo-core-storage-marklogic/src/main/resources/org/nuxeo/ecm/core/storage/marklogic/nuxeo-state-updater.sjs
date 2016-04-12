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
  xdmp.log("Path the document.");
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
  xdmp.log("Patch this part : document=" + document + ", patch=" + patch + ", typeof document=" + typeof document);
  for (var key in patch) {
    if (patch[key] === null) {
      // Remove the node from document
      delete document[key];
    } else if (typeof patch[key] === 'object') {
      if (typeof document[key] === 'undefined') {
        // Object doesn't exist in document
        document[key] = patch[key];
      } else {
        // TODO handle array
        // Loop on sub structure
        patchDocument(document[key], patch[key]);
      }
    } else {
      // Primitive value
      document[key] = patch[key];
    }
  }
}

exports.PUT = put;
