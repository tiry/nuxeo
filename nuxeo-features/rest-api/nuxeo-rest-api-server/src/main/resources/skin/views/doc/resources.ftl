{
    "apiVersion": "1.0.0",
    "swaggerVersion": "1.2",
    "apis": [
        {
            "path": "/path.{format}",
            "description": "Access documents by their path"
        },
        {
            "path": "/id.{format}",
            "description": "Access documents by their id"
        },
        {
            "path": "/query.{format}",
            "description": "Document Search"
        },
        {
            "path": "/blobAdapter.{format}",
            "description": "Get main document blob"
        },
        {
            "path": "/automation.{format}",
            "description": "Run automation operations"
        },
        {
            "path": "/user.{format}",
            "description": "Access users"
        },
        {
            "path": "/group.{format}",
            "description": "Access groups"
        },
        {
            "path": "/directory.{format}",
            "description": "Access directories"
        },
        {
            "path": "/childrenAdapter.{format}",
            "description": "Get the children of a document"
        },
        {
            "path": "/searchAdapter.{format}",
            "description": "Search for documents"
        },
        {
            "path": "/ppAdapter.{format}",
            "description": "Execute a page provider"
        },
        {
            "path": "/aclAdapter.{format}",
            "description": "View the acl of a document"
        },
        {
            "path": "/auditAdapter.{format}",
            "description": "View the audit trail of a document"
        },
        {
            "path": "/boAdapter.{format}",
            "description": "Business object adapter on a document"
        },
        {
            "path": "/workflow.{format}",
            "description": "Browse and start workflow instances"
        },
        {
            "path": "/workflowModel.{format}",
            "description": "List workflow models"
        },
        {
            "path": "/task.{format}",
            "description": "Browse and complete task"
        },
        {
            "path": "/renditionAdapter.{format}",
            "description": "Rendition on a document"
        },
        {
            "path": "/convertAdapter.{format}",
            "description": "Convert Blobs"
        }
    ],
    "authorizations": {
        "basicAuth": {
            "type": "basicAuth"
        }
    },
    "basePath": "${Context.serverURL}${This.path}"

}
