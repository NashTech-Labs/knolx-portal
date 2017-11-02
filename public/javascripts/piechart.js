$( document ).ready(function() {

$("text.highcharts-credits").hide();

     jsRoutes.controllers.SessionsController.piechart().ajax(
        {
            type: 'POST',
            processData: false,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;

                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data){
            var values = JSON.parse(data);
            var items = [];
            var series = [];

            var categoryInfo = values['categoryInformation']
            for (var i = 0; i < categoryInfo.length; i++) {
                var dataSubCategory = [];
                var item = {
                    "name": categoryInfo[i].categoryName,
                    "y" :   parseFloat(categoryInfo[i].totalSessionCategory/values.totalSession),
                    "drilldown": categoryInfo[i].categoryName
                };
                items.push(item);

                for(var j = 0; j < categoryInfo[i]["subCategoryInfo"].length; j++ ){
                    var dataSub = [categoryInfo[i]["subCategoryInfo"][j].subCategoryName,categoryInfo[i]["subCategoryInfo"][j].totalSessionSubCategory ];
                    dataSubCategory.push(dataSub);
                }
            var drillData = {
                "name": categoryInfo[i].categoryName ,
                "id": categoryInfo[i].categoryName,
                "data" : dataSubCategory
            };
        series.push(drillData);
            }
            var myChart = Highcharts.chart('container', {
                     chart: {
                     type: 'pie'
                 },
                     title: {
                         text: 'Browser market shares. January, 2015 to May, 2015'
                     },
                     subtitle: {
                         text: 'Click the slices to view versions. Source: netmarketshare.com.'
                     },
                     plotOptions: {
                         pie: {
                             allowPointSelect: true,
                             cursor: 'pointer',
                             depth: 35,
                             dataLabels: {
                                 enabled: false
                             },
                             showInLegend: true
                         }
                     },

                     tooltip: {
                         headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                         pointFormat: '<span style="color:{point.color}">{point.name}</span>: <b>{point.percentage:.1f}%</b> of total<br/>'
                     },
                     series: [{
                         name: 'Primary Category',
                         colorByPoint: true,
                         data: items
                     }],
                     drilldown: {
                         series: series
                     },
                     responsive: {
                       rules: [{
                         condition: {
                           maxWidth: 500
                         },
                         chartOptions: {
                           legend: {
                             enabled: false
                           }
                         }
                       }]
                     }
                 });
           }
        })
});
