var mockClusterData ={
  name: "joinDataFlow",
  nodes:[
    {"id":0, "name":"127.0.1.1:8999", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Master", "status":"completed", "group":0},
    {"id":1, "name":"127.0.1.10:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"running", "group":1},
    {"id":2, "name":"127.0.1.21:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"running", "group":1},
    {"id":3, "name":"127.0.1.31:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"waiting", "group":2},
    {"id":4, "name":"127.0.1.41:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"waiting", "group":2},
    {"id":5, "name":"127.0.1.51:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"waiting", "group":2},
    {"id":6, "name":"127.0.1.61:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"waiting", "group":4},
    {"id":7, "name":"127.0.1.17:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"waiting", "group":4},
    {"id":8, "name":"127.0.1.17:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"waiting", "group":6},
    {"id":9, "name":"127.0.1.10:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"running", "group":1},
    {"id":10, "name":"127.0.1.21:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"running", "group":1},
    {"id":11, "name":"127.0.1.31:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"waiting", "group":2},
    {"id":12, "name":"127.0.1.41:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"waiting", "group":2},
    {"id":13, "name":"127.0.1.51:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"waiting", "group":2},
    {"id":14, "name":"127.0.1.61:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"waiting", "group":4},
    {"id":15, "name":"127.0.1.17:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"waiting", "group":4},
    {"id":16, "name":"127.0.1.17:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"waiting", "group":6},
    {"id":17, "name":"127.0.1.17:10010", "version":"0.0.1", "startTime":"123", "endTime":"456", "type":"Worker", "status":"waiting", "group":6}
  ],
  links:[
    {"source":1,"target":0,"value":8},
    {"source":2,"target":0,"value":10},
    {"source":3,"target":0,"value":6},
    {"source":3,"target":0,"value":1},
    {"source":4,"target":0,"value":1},
    {"source":5,"target":0,"value":1},
    {"source":6,"target":0,"value":1},
    {"source":7,"target":0,"value":1},
    {"source":8,"target":0,"value":1},
    {"source":9,"target":0,"value":8},
    {"source":10,"target":0,"value":10},
    {"source":11,"target":0,"value":6},
    {"source":12,"target":0,"value":1},
    {"source":13,"target":0,"value":1},
    {"source":14,"target":0,"value":1},
    {"source":15,"target":0,"value":1},
    {"source":16,"target":0,"value":1},
    {"source":17,"target":0,"value":1}
  ]
};