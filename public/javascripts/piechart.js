$(function ()  {

var startDate = moment().subtract(3, 'months').startOf('day').format('YYYY-MM-DD HH:mm').toString();
var endDate = moment().endOf('day').format('YYYY-MM-DD HH:mm ').toString();

pieChar(startDate,endDate);

    $('#demo').daterangepicker({
        "startDate": moment().subtract(1,'months'),
        "endDate": moment()
    }, function(start, end, label) {
        var startDate = start.format('YYYY-MM-DD h:mm A');
        var endDate = end.format('YYYY-MM-DD h:mm A');
        console.log("New date range selected: " + startDate + ' to ' + endDate + ' (predefined range: ' + label);
        pieChar(startDate, endDate);
});

});


function pieChar(startDate, EndDate) {
            var formData = new FormData();
            formData.append("startDate", startDate);
            formData.append("endDate", EndDate);
            console.log("email in formData = " + formData.get('startDate'));

        jsRoutes.controllers.SessionsController.piechart().ajax(
        {
           type: 'POST',
            processData: false,
            contentType: false,
            data: formData,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;
                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data){
            console.log("We are in filterSession functionhh");
            console.log(data);
            var values = JSON.parse(data);
            console.log(values[1][0].subCategoryName);
            var items = [];
            var series = [];

            /*var categoryInfo = values['categoryInformation']
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
                 });*/
           }, error: function (er) {
            console.log("No session found!");
        }
    })
}
