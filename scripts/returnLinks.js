const cheerio = require("cheerio");

(function(response, meta) {
   var $ = cheerio.load(response);
   var links = [];
   $('a').each(function ()
   {
	  console.log($(this).attr('href'));
      links.push($(this).attr('href'));
   });
   return JSON.stringify(links);
})
