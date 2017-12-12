$(function () {
    var startDate = moment().subtract(1, 'years').startOf('day').format('YYYY-MM-DD HH:mm').toString();
    var endDate = moment().endOf('day').format('YYYY-MM-DD HH:mm').toString();

    if (sessionStorage.startDate === undefined) {
        sessionStorage.setItem("startDate", startDate);
    }

    if (sessionStorage.endDate === undefined) {
        sessionStorage.setItem("endDate", endDate);
    }

    var startDateSessionStorage = moment(sessionStorage.startDate).startOf('day').format('YYYY-MM-DD HH:mm').toString();
    var endDateSessionStorage = moment(sessionStorage.endDate).endOf('day').format('YYYY-MM-DD HH:mm').toString();

    analysis(startDateSessionStorage, endDateSessionStorage);

    $('#demo').daterangepicker({
        "startDate": new Date(sessionStorage.startDate),
        "endDate": new Date(sessionStorage.endDate)
    }, function (start, end) {
        sessionStorage.setItem("startDate", start);
        sessionStorage.setItem("endDate", end);

        var startDate = start.format('YYYY-MM-DD h:mm A');
        var endDate = end.format('YYYY-MM-DD h:mm A');

        analysis(startDate, endDate);
    });

});

function analysis(startDate, EndDate) {
    pieChart(startDate, EndDate);
    columnChart(startDate, EndDate);
    lineGraph(startDate, EndDate);
    leaderBoard(startDate, EndDate);
}

function columnChart(startDate, EndDate) {
    var formData = {
        "startDate": startDate,
        "endDate": EndDate
    };
    jsRoutes.controllers.KnolxAnalysisController.renderColumnChart().ajax(
        {
            type: 'POST',
            processData: false,
            contentType: 'application/json',
            data: JSON.stringify(formData),
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;
                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data) {

                var subCategoryData = [];
                var columnGraphXAxis = [];

                for (var i = 0; i < data.length; i++) {
                    var dataSub = data[i].subCategoryName;
                    var sessionSub = data[i].totalSessionSubCategory;

                    subCategoryData.push(dataSub);
                    columnGraphXAxis.push(sessionSub);
                }

                var columnGraph = Highcharts.chart('column-graph', {
                    title: {
                        text: 'Session(s) Sub-Category Analysis'
                    },
                    credits: {
                            enabled: false
                    },
                    xAxis: {
                        categories: subCategoryData
                    },
                    yAxis: {
                        title: {
                            text: 'Total Sessions In Month'
                        }
                    },

                    series: [{
                        type: 'column',
                        colorByPoint: true,
                        data: columnGraphXAxis,
                        showInLegend: false
                    }]
                });
            }
        });
}

function pieChart(startDate, EndDate) {
    var formData = {
        "startDate": startDate,
        "endDate": EndDate
    };

    jsRoutes.controllers.KnolxAnalysisController.renderPieChart().ajax(
        {
            type: 'POST',
            processData: false,
            contentType: 'application/json',
            data: JSON.stringify(formData),
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;
                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data) {
                var items = [];
                var series = [];

                var categoryInfo = data['categoryInformation'];

                for (var i = 0; i < categoryInfo.length; i++) {
                    var dataSubCategory = [];
                    var item = {
                        "name": categoryInfo[i].categoryName,
                        "y": parseFloat(categoryInfo[i].totalSessionCategory / data.totalSession),
                        "drilldown": categoryInfo[i].categoryName
                    };
                    items.push(item);

                    for (var j = 0; j < categoryInfo[i]["subCategoryInfo"].length; j++) {
                        var dataSub = [categoryInfo[i]["subCategoryInfo"][j].subCategoryName, categoryInfo[i]["subCategoryInfo"][j].totalSessionSubCategory];
                        dataSubCategory.push(dataSub);
                    }
                    var drillData = {
                        "name": categoryInfo[i].categoryName,
                        "id": categoryInfo[i].categoryName,
                        "data": dataSubCategory
                    };
                    series.push(drillData);
                }

                var column = Highcharts.chart('pie-chart', {
                    chart: {
                        type: 'pie'
                    },
                    title: {
                        text: 'Session(s) Category Analysis'
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
                    credits: {
                        enabled: false
                    },
                    tooltip: {
                        headerFormat: '<span style="font-size:11px">{series.name}</span><br>',
                        pointFormat: '<span style="color:{point.color}">{point.name}</span>: <b>{point.percentage:.1f}%</b> of total<br/>'
                    },
                    series: [{
                        name: 'Category',
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
}

function lineGraph(startDate, EndDate) {

    var formData = {
        "startDate": startDate,
        "endDate": EndDate
    };
    jsRoutes.controllers.KnolxAnalysisController.renderLineChart().ajax(
        {
            type: 'POST',
            processData: false,
            contentType: 'application/json',
            data: JSON.stringify(formData),
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;
                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (data) {

                var seriesData = [];
                var xAxisData = [];
                for (var i = 0; i < data.length; i++) {

                    xAxisData.push(data[i].monthName);
                    seriesData.push(data[i].total);
                }
                Highcharts.chart('line-graph', {
                    chart: {
                        type: 'area'
                    },
                    title: {
                        text: 'Session(s) Monthly Analysis'
                    },
                    credits: {
                        enabled: false
                    },
                    xAxis: {
                        categories: xAxisData
                    },
                    yAxis: {
                        title: {
                            text: 'Total Sessions In Month'
                        },
                        labels: {
                            formatter: function () {
                                return this.value;
                            }
                        }
                    },
                    tooltip: {
                        split: true,
                        valueSuffix: ' Session(s)'
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
        })
}

function leaderBoard(startDate, EndDate) {

    var formData = {
            "startDate": startDate,
            "endDate": EndDate
    };
    jsRoutes.controllers.KnolxAnalysisController.leaderBoard().ajax(
        {
            type: 'POST',
            processData: false,
            contentType: 'application/json',
            data: JSON.stringify(formData),
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;
                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (users) {
                var usersFound = "";
                for(var user = 0; user < users.length; user++) {
                    usersFound += '<tr class="table-header-color">' +
                                 '<td>' + users[user] + '</td>' +
                                 '</tr>';
                }
                $('#leaderBoard').html(usersFound);
            },
            error: function(er) {
                $('#leaderBoard').html(
                    "<tr><td align='center'><i class='fa fa-database' aria-hidden='true'></i><span class='no-record-found'>" + er.responseText + "</span></td></tr>"
                );
            }
        }
    )
}
