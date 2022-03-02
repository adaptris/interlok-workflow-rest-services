# interlok-workflow-rest-services

[![GitHub tag](https://img.shields.io/github/tag/adaptris/interlok-workflow-rest-services.svg)](https://github.com/adaptris/interlok-workflow-rest-services/tags) ![license](https://img.shields.io/github/license/adaptris/interlok-workflow-rest-services.svg) [![Actions Status](https://github.com/adaptris/interlok-workflow-rest-services/workflows/Java%20CI/badge.svg)](https://github.com/adaptris/interlok-workflow-rest-services/actions) [![codecov](https://codecov.io/gh/adaptris/interlok-workflow-rest-services/branch/develop/graph/badge.svg)](https://codecov.io/gh/adaptris/interlok-workflow-rest-services) [![Total alerts](https://img.shields.io/lgtm/alerts/g/adaptris/interlok-workflow-rest-services.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/adaptris/interlok-workflow-rest-services/alerts/) [![Language grade: Java](https://img.shields.io/lgtm/grade/java/g/adaptris/interlok-workflow-rest-services.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/adaptris/interlok-workflow-rest-services/context:java)

REST style API for Interlok.

## Health-Check

* As of 3.10.1 this will add a mapped diagnostic context entry of `WorkflowHealthCheckComponent` against the key `ManagementComponent`; you can use this to filter your log file.

Allows you to see a status (started, stopped, etc) of all Interlok channels and workflows in JSON form.

### Installation

Drop the __interlok-workflow-rest-services.jar__ built from this project in to your Interlok __lib__ directory, then modify your __bootstrap.properties__ to make sure the managementComponents property contains all of "__jetty__", "__jmx__" and "__health-check__".
```
managementComponents=jetty:jmx:health-check
```
Optionally, you can also set the property named __rest.health-check.path__, which directly affects the REST API URL path.  The default value is; "__/workflow-health-check/*__".

### Running

There are 3 modes of operation a health-check, a liveness probe and a readiness probe.

#### HealthCheck mode

```
curl -si http://localhost:8080/workflow-health-check
HTTP/1.1 200 OK
Content-Type: application/json
Transfer-Encoding: chunked
Server: Jetty(9.4.29.v20200521)

{"adapters":[{"adapter-state":{"id":"MyInterlokInstance","state":"StartedState","channel-states":[{"channel-state":[{"id":"jetty1","state":"StartedState","workflow-states":[{"workflow-state":{"id":"jetty-workflow","state":"StartedState"}}]},{"id":"jetty2(not-started)","state":"ClosedState","workflow-states":[{"workflow-state":{"id":"jetty-workflow","state":"ClosedState"}}]},{"id":"jetty3","state":"StartedState","workflow-states":[{"workflow-state":{"id":"jetty-workflow","state":"StartedState"}}]}]}]}}]}
```

This will return a JSON array, with all of your adapter, channel and workflow states.  A state, can be one of either; __StartedState__, __InitialisedState__, __StoppedState__ or __ClosedState__. For example:

```json
{
    "adapters": [{
        "adapter-state": {
            "id": "MyInterlokInstance",
            "state": "StartedState",
            "channel-states": [{
                "channel-state": [{
                    "id": "jetty1",
                    "state": "StartedState",
                    "workflow-states": [{
                        "workflow-state": {
                            "id": "jetty-workflow",
                            "state": "StartedState"
                        },
                        "workflow-state": {
                            "id": "another-workflow",
                            "state": "StartedState"
                        }
                    }]
                }, {
                    "id": "jetty2(not-started)",
                    "state": "ClosedState",
                    "workflow-states": [{
                        "workflow-state": {
                            "id": "jetty-workflow",
                            "state": "ClosedState"
                        }
                    }]
                }, {
                    "id": "jetty3",
                    "state": "StartedState",
                    "workflow-states": [{
                        "workflow-state": {
                            "id": "jetty-workflow",
                            "state": "StartedState"
                        }
                    }]
                }]
            }]
        }
    }]
}
```


#### Liveness probe

* From _3.10.2_

```
$ curl -si http://localhost:8080/workflow-health-check/alive
HTTP/1.1 200 OK
Content-Type: application/json
Transfer-Encoding: chunked
Server: Jetty(9.4.29.v20200521)


```

This just returns a `200 OK`, indicating that the Interlok instance considers itself _alive_. __Alive does not mean useful__. There is no response data in the event of a `200 OK`.

#### Readiness probe

* From _3.10.2_

```
$ curl -si http://localhost:8080/workflow-health-check/ready
HTTP/1.1 503 Service Unavailable
Content-Type: application/json
Transfer-Encoding: chunked
Server: Jetty(9.4.29.v20200521)

{"failure": "jetty2(not-started) is not started"}
```

This returns a `200 OK` if all the channels and workflows are started; otherwise a `503 Unavailable` is returned. There is no response data in the event of a `200 OK`; in the event of a `503 Unavailable` only the first component that was "not-started" will be reported to shortcut response times.


## Cluster Manager

* As of 3.10.1 this will add a mapped diagnostic context entry of `ClusterManagerComponent` against the key `ManagementComponent`; you can use this to filter your log file.

Allows you to see a full list of known Interlok cluster instances in this instances cluster; JSON formatted.

### Installation

Drop the __interlok-workflow-rest-services.jar__ built from this project in to your Interlok __lib__ directory, then modify your __bootstrap.properties__ to make sure the managementComponents property contains all of "__jetty__", "__jmx__, "__cluster__", " and "__cluster-rest__".
```
managementComponents=jetty:jmx:cluster
```

The __cluster__ component enables basic clustering, __cluster-rest__ enables querying of the known instances.

Optionally, you can also set the property named __rest.cluster-manager.path__, which directly affects the REST API URL path.  The default value is; "__/cluster-manager/*__".

### Running


Using your favourite HTTP GET/POST tool, make a GET request to the running Interlok instance;
```
http GET http://<host>:<port>/cluster-manager
```

This will return a JSON array, with all of the known cluster instances.

Below is an example of the resulting JSON.

```json
{
  "java.util.Collection": [
    {
      "com.adaptris.mgmt.cluster.ClusterInstance": {
        "cluster-uuid": "97015aa0-f9b8-4bd1-93fb-01922b827d08",
        "jmx-address": "service:jmx:jmxmp://localhost:5555",
        "unique-id": "MyInterlokInstance"
      }
    }
  ]
}
```


## Workflow Rest Services

* As of 3.10.1 this will add a mapped diagnostic context entry of `WorkflowServicesComponent` against the key `ManagementComponent`; you can use this to filter your log file.

### Installation

Drop the __interlok-workflow-rest-services.jar__ built from this project in to your Interlok __lib__ directory along with the following __interlok-jmx.jar__ and __interlok-jmx-client.jar__.

Then modify your __bootstrap.properties__ to make sure the managementComponents property contains all of "__jetty__", "__jmx__" and "__rest__".
```
managementComponents=jetty:jmx:rest
```

### The API definition ###

There is a single http GET API endpoint that will return an OpenApi 3.0.1 API definition.

You can reach this definition with a HTTP GET to the root url of ;
`http://<host>:<port>/workflow-services/`

The definition is dynamically built in OpenApi 3.0.1 yaml format to include all of your Interlok workflows; a brief example;

```yaml
openapi: 3.0.1
info:
  title: Interlok Workflow REST services
  description: A REST entry point into your Interlok workflows.
  version: "1.0"
servers:
  - url: 'http://yourhost:yourport'
  /workflow-services/myAdapterId/myChannelId/myWorkflowId:
    post:
      description: Post your message directly into your Interlok workflow.
      requestBody:
        content:
          text/plain:
            schema:
              type: string
            examples:
              '0':
                value: MyMessageContent
      responses:
        '200':
          description: The response content after your workflow has processed the incoming message.
          content:
            text/plain:
              schema:
                type: string
              examples:
                '0':
                  value: MyMessageResponseContent
      servers:
        - url: 'http://yourhost:yourport'
    servers:
      - url: 'http://yourhost:yourport'
```

By pointing an OpenApi 3.0.1 tool, such as swagger to the definition url you can visualize the full API available for this instance of Interlok.


### Injecting your messages into your Interlok workflows ###

In true restful style you will POST to the base url with all of the Interlok instance id, channel id and workflow id built into the full url;

`http://<host>:<port>/workflow-services/myInterlokId/myChannelId/myWorkflowId`

The Interlok message content will be drawn from the body of your POST request.

Any http headers will be converted into Interlok message metadata.

If your message was successfully submitted to the workflow then you will get a http status 200 code response with the updated content as the body of the response.

Should your request fail or cause an error, you will receive a http status 400 code along with details of the error in the body of the response.
