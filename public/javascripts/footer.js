$(function () {

    google.load("feeds", "1");

       function initialize() {
          var feed = new google.feeds.Feed("https://blog.knoldus.com/feed",{
            api_key : '01twph0otyxl51pzey8cczxjdse5dlribpyqurjn',
          });
          feed.load(function(result) {
          if (!result.error) {
             var topFiveBlogs = result.feed.entries.splice(0, 5);
            var container = document.getElementById("feed");
	    var uiTag = document.createElement('ul');
	    container.appendChild(uiTag);
            for (var i = 0; i < topFiveBlogs.length; i++) {
              var entry = topFiveBlogs[i];
              var aTag = document.createElement('a');
	      var liTag= document.createElement('li');
             aTag.appendChild(document.createTextNode(entry.title));

             aTag.setAttribute('href',topFiveBlogs[i].link);
              aTag.setAttribute('target',"blank");
   		   uiTag.appendChild(liTag);
     	       	  liTag.appendChild(aTag);

            }
          }
          });
        }
        google.setOnLoadCallback(initialize);

$("#current-year").html(new Date().getFullYear());

});
