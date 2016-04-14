/*
 * @param context An object containing service request context information such as input document types and URIs, and
 *    output types accepted by the caller.
 * @param params An object containing extension-specific parameter values supplied by the client, if any. In our case
 *    the document id.
 * @param input The data from the request body. The update to apply to document.
 */
function put(context, params, input) {
  // TODO find why the method below raises : Internal Server Error. Server Message: declareUpdate(); -- Operation not allowed on the currently executing transaction with identifier declareUpdate .
  //declareUpdate();
  // Validate input
  if (input instanceof ValueIterator) {
    returnErrToClient(400, 'Bad Request', 'Only one lock is allowed in manager.');
  }
  // Validate parameter
  var uri = params.uri;
  if (uri == null) {
    returnErrToClient(400, 'Bad Request', 'Document uri to lock is missing.');
  }
  var result;

  var document = cts.doc(uri);
  if (typeof document === 'undefined' || document === null) {
    returnErrToClient(404, 'Document Not Found', 'Document with uri=' + uri + ' could not be found.');
  }
  document = document.toObject();
  var lock = input.toObject();
  var oldOwner = document['ecm__lockOwner'];
  if (typeof oldOwner === 'undefined') {
    // document is not locked, lock it
    document['ecm__lockOwner'] = lock['ecm__lockOwner'];
    document['ecm__lockCreated'] = lock['ecm__lockCreated'];
    xdmp.documentInsert(uri, document);

    result = {};
  } else {
    result = {
      'ecm__lockOwner': oldOwner,
      'ecm__lockCreated': document['ecm__lockCreated']
    };
  }
  context.outputTypes = ['application/json'];
  return result;
}

function deleteFunction(context, params) {
  // TODO find why the method below raises : Internal Server Error. Server Message: declareUpdate(); -- Operation not allowed on the currently executing transaction with identifier declareUpdate .
  //declareUpdate();
  // Validate parameters
  var uri = params.uri;
  var owner = params.owner;
  if (uri == null) {
    returnErrToClient(400, 'Bad Request', 'Document uri to lock is missing.');
  }
  var result;

  var document = cts.doc(uri);
  if (typeof document === 'undefined' || document === null) {
    returnErrToClient(404, 'Document Not Found', 'Document with uri=' + uri + ' could not be found.');
  }
  document = document.toObject();
  var oldOwner = document['ecm__lockOwner'];
  var oldCreated = document['ecm__lockCreated'];
  if (typeof owner === 'undefined' || owner === null || owner === '') {
    // unconditional remove
    delete document['ecm__lockOwner'];
    delete document['ecm__lockCreated'];
    xdmp.documentInsert(uri, document);

    result = {
      'ecm__lockOwner': oldOwner,
      'ecm__lockCreated': oldCreated
    };
  } else {
    // remove if owner matches or null
    if (owner === oldOwner) {
      delete document['ecm__lockOwner'];
      delete document['ecm__lockCreated'];
      xdmp.documentInsert(uri, document);

      result = {
        'ecm__lockOwner': oldOwner,
        'ecm__lockCreated': oldCreated
      };
    } else if (typeof oldOwner === 'undefined' || oldOwner === null) {
      result = {};
    } else {
      // lock owner didn't match
      result = {
        'ecm__lockOwner': oldOwner,
        'ecm__lockCreated': oldCreated,
        'failed': true
      }
    }
  }
  context.outputTypes = ['application/json'];
  return result;
}

function returnErrToClient(statusCode, statusMsg, body) {
  fn.error(null, 'RESTAPI-SRVEXERR',
    xdmp.arrayValues([statusCode, statusMsg, body]));
  // unreachable - control does not return from fn.error.
}

exports.PUT = put;
exports.DELETE = deleteFunction;
