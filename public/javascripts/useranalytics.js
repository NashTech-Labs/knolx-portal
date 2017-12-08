$(function () {
    ko.applyBindings(new UserAnalytics());
});

function UserAnalytics() {
    var self = this;
    self.emailList = ko.observableArray([]);
    self.email = ko.observableArray([]);
    self.banCount = ko.observable();
    self.sessionNotAttend = ko.observable();
    self.totalKnolx = ko.observable();
    self.totalMeetups = ko.observable();

    FetchEmailList(null);

    self.userDataHandler = function (email) {
        console.log("email--->" + email);
        jsRoutes.controllers.KnolxUserAnalysisController.userAnalysis(email).ajax({
            type: "POST",
            processData: false,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;

                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (values) {
                $('#user-analytics').show();
                console.log("user data--->" + JSON.stringify(values));
                self.email(values["email"]);
                self.banCount(values["banCount"]);
                self.sessionNotAttend(values["didNotAttendCount"]);
                self.totalKnolx(values["totalKnolx"]);
                self.totalMeetups(values["totalMeetUps"]);

                var sessions = values["sessions"];
                var xaxisData = [];
                var coreMemberResponse = [];
                var nonCoreMemberResponse = [];

                for (var i = 0; i < sessions.length; i++) {
                    var session = sessions[i].topic;
                    var coreResponse = sessions[i].coreMemberResponse;
                    var nonCoreResponse = sessions[i].nonCoreMemberResponse;

                    xaxisData.push(session);
                    coreMemberResponse.push(coreResponse);
                    nonCoreMemberResponse.push(nonCoreResponse);
                }

                var columnGraph = Highcharts.chart('response-comparison', {
                    chart: {
                        type: 'areaspline'
                    },
                    title: {
                        text: 'Comparison Between Core Member Response and Non-Core Member Response '
                    },
                    legend: {
                        layout: 'vertical',
                        align: 'left',
                        verticalAlign: 'top',
                        x: 150,
                        y: 100,
                        floating: true,
                        borderWidth: 1,
                        backgroundColor: (Highcharts.theme && Highcharts.theme.legendBackgroundColor) || '#FFFFFF'
                    },
                    xAxis: {
                        categories: xaxisData,
                        plotBands: [{
                            color: 'rgba(68, 170, 213, .2)'
                        }]
                    },
                    yAxis: {
                        title: {
                            text: 'Percentage'
                        }
                    },
                    tooltip: {
                        shared: true,
                        valueSuffix: ' %'
                    },
                    credits: {
                        enabled: false
                    },
                    plotOptions: {
                        areaspline: {
                            fillOpacity: 0.5
                        }
                    },
                    series: [{
                        name: 'CoreMember',
                        data: coreMemberResponse
                    }, {
                        name: 'Non Core Member',
                        data: nonCoreMemberResponse
                    }]
                });

            }, error: function (er) {
                console.log(er);
            }
        })
    };

    $('#search-user').keyup(function () {
        FetchEmailList(this.value);
    });

    function FetchEmailList(email) {
        jsRoutes.controllers.KnolxUserAnalysisController.sendUserList(email).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    console.log(values);
                    self.emailList(values);
                }
            }
        )
    }


}