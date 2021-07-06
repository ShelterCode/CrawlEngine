const cheerio = require("cheerio");

(function(response, meta) {
   var $ = cheerio.load(response);
   return $('title').text();
})
