var interval    = 432000000;
//var startDate   = (new Date(Math.floor((new Date()).getTime() / interval) * interval)).toString();
//
x =  new Date(0);
x.setUTCSeconds(1377680400);

var startDate   = (x).toString();
//alert(startDate);

//var endDate     = (new Date(Math.ceil((new Date()).getTime() / interval) * interval)/*).toString();
x =  new Date(0);
x.setUTCSeconds(1380646800);
var endDate     = (x).toString();
//alert(endDate);

var timeRefresh = 200;
var dotTension  = 3.5;
var dotDelay    = 1500;
var marginSpan  = 250;