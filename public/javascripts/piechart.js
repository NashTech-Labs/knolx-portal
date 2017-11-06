$(function ()  {

var startDate = moment().subtract(3, 'months').startOf('day').format('YYYY-MM-DD HH:mm').toString();
var endDate = moment().endOf('day').format('YYYY-MM-DD HH:mm ').toString();

    analysis(startDate,endDate);

    $('#demo').daterangepicker({
        "startDate": moment().subtract(1,'months'),
        "endDate": moment().format('DD-MM-YYYY')

    }, function(start, end, label) {
        var startDate = start.format('YYYY-MM-DD h:mm A');
        var endDate = end.format('YYYY-MM-DD h:mm A');
        console.log("New date range selected: " + startDate + ' to ' + endDate + ' (predefined range: ' + label);
        analysis(startDate, endDate);
    });

});


function analysis(startDate, EndDate) {
            var formData = new FormData();
            formData.append("startDate", startDate);
            formData.append("endDate", EndDate);

        jsRoutes.controllers.AnalysisController.pieChart().ajax(
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
            var values = JSON.parse(data);

            console.log(values);

              pieChart(values);

              columnChart(values[1]);

              lineGraph(values[2]);

              console.log("aassdfhhvh")
           }, error: function (er) {
            console.log("No session found!");
        }
    })
}

function columnChart(data) {
        console.log("------->"+data);
        var subCategoryData = [];
        var columnGraphXAxis = [];
        for(var i =0; i < data.length; i++) {
        var dataSub = data[i].subCategoryName;
        var sessionSub = data[i].totalSessionSubCategory;

        subCategoryData.push(dataSub);
        columnGraphXAxis.push(sessionSub);

        }

        var columnGraph = Highcharts.chart('column-graph', {

       title: {
           text: 'Knolx Session Sub-Category Analysis'
       },

       subtitle: {
           text: 'Plain'
       },

       xAxis: {
           categories: subCategoryData
       },

       series: [{
           type: 'column',
           colorByPoint: true,
           data: columnGraphXAxis,
           showInLegend: false
       }]
   });

}

function pieChart(values) {

    console.log(values[0]['categoryInformation']);
    var items = [];
    var series = [];

    var categoryInfo = values[0]['categoryInformation'];


    for (var i = 0; i < categoryInfo.length; i++) {
        var dataSubCategory = [];
        var item = {
            "name": categoryInfo[i].categoryName,
            "y" :   parseFloat(categoryInfo[i].totalSessionCategory/values[0].totalSession),
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

    var column = Highcharts.chart('pie-chart', {
             chart: {
             type: 'pie'
         },
             title: {
                 text: 'Knolx Session Analysis'
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

function lineGraph(data) {

    var seriesData = [];
    var xAxisData = [];

    for (var i= 0; i < data.length; i++ ) {

    xAxisData.push(data[i].monthName);
    seriesData.push(data[i].total);
    }

    Highcharts.chart('line-graph', {
        chart: {
            type: 'area'
        },
        title: {
            text: 'Knolx'
        },
        xAxis: {
            categories: xAxisData
        },
        yAxis: {
            title: {
                text: 'Total Session In Month'
            },
            labels: {
                formatter: function () {
                    return this.value ;
                }
            }
        },
        tooltip: {
            split: true,
            valueSuffix: ' Sessions'
        },
        plotOptions: {
            area: {
                stacking: 'normal',
                lineColor: '#66FF66',
                lineWidth: 2,
                marker: {
                    lineWidth: 2,
                    lineColor: '#ff6666'
                }
            }
        },
        series: [{
            name: 'Monthly Information',
            data: seriesData
        }]
    });
}
