$( document ).ready(function() {

     jsRoutes.controllers.SessionsController.piechart().ajax(
        {
                        type: "GET",
                        processData: false,
                        success: function (data){
                        var values = JSON.parse(data);

                var items = [];
                for (var ln = 0; ln < values['categoryInformation'].length; ln++) {
                    var item = {
                    "name": values['categoryInformation'][ln].categoryName,
                    "y" :   parseFloat(values['categoryInformation'][ln].totalSession/values.total)
                 };
                items.push(item);
            }
           var myChart = Highcharts.chart('container', {
            chart: {
                plotBackgroundColor: null,
                plotBorderWidth: null,
                plotShadow: false,
                type: 'pie'
            },
            title: {
                text: 'Knolx Session Analysis'
            },
            tooltip: {
                pointFormat: '{series.name}: <b>{point.percentage:.1f}%</b>'
            },
            plotOptions: {
                pie: {
                    allowPointSelect: true,
                    cursor: 'pointer',
                    dataLabels: {
                        enabled: true,
                        format: '<b>{point.name}</b>: {point.percentage:.1f} %',
                        style: {
                            color: (Highcharts.theme && Highcharts.theme.contrastTextColor) || 'black'
                        }
                    }
                }
            },
            series: [{
                name: 'Brands',
                colorByPoint: true,
                data: items
            }]
            })
           }
        })
});
