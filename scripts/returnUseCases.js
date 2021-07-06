const cheerio = require("cheerio");

(function(response, meta) {

   var metaData = JSON.parse(meta);
   var $ = cheerio.load(response);
   var links = [];
   $('a').each(function ()
   {
	  var href = $(this).attr('href');
	  if(href && href.indexOf('facebook') == -1
	  && href.indexOf('linkedin') == -1
	  && href.indexOf('twitter') == -1
	  && href.indexOf('javascript') == -1
	  &&(href.startsWith('/')||href.indexOf('datafiniti.co')!=-1)
      ){
      	links.push(href);
  	  }
   });

   var returnObject = null;
   if(metaData.requestedUrl.indexOf('/use-case/')!=-1){
	 var summary = $('h1').text();
     var detail = $('h1').next().children().text();
   	 returnObject = { data:{ title:$('title').text(), url: metaData.requestedUrl, summary: summary, detail: detail, urlCount: links.length}, recurseUrlList:links};
   } else {
	 returnObject = { command:{exclude:true}, recurseUrlList:links}
   }
   return JSON.stringify(returnObject);
})
