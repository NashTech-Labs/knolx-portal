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
        self.email(email);
        fetchBanCount(email);
        fetchResponseRatingForComparison(email);
        fetchUserDidNotAttendSessionCount(email);
        fetchUserTotalKnolx(email);
        fetchUserTotalMeetUps(email);

    };

    $('#search-user').keyup(function () {
        FetchEmailList(this.value);
    });

    function FetchEmailList(email) {
        jsRoutes.controllers.UsersController.usersList(email).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    self.emailList(values);
                }
            }
        )
    }

    function fetchBanCount(email) {
        jsRoutes.controllers.KnolxUserAnalysisController.getBanCount(email).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    self.banCount(values["banCount"]);
                }
            }
        )
    }

    function fetchUserTotalKnolx(email) {
        jsRoutes.controllers.KnolxUserAnalysisController.getUserTotalKnolx(email).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    self.totalKnolx(values["totalKnolx"]);
                }
            }
        )
    }

    function fetchUserTotalMeetUps(email) {
        jsRoutes.controllers.KnolxUserAnalysisController.getUserTotalMeetUps(email).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    self.totalMeetups(values["totalMeetUps"]);
                }
            }
        )
    }

    function fetchUserDidNotAttendSessionCount(email) {
        jsRoutes.controllers.KnolxUserAnalysisController.getUserDidNotAttendSessionCount(email).ajax(
            {
                type: "POST",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {
                    self.sessionNotAttend(values["didNotAttendCount"]);
                }
            }
        )
    }

    function fetchResponseRatingForComparison(email) {
        jsRoutes.controllers.KnolxUserAnalysisController.userSessionsResponseComparison(email).ajax({
            type: "POST",
            processData: false,
            beforeSend: function (request) {
                var csrfToken = document.getElementById('csrfToken').value;

                return request.setRequestHeader('CSRF-Token', csrfToken);
            },
            success: function (sessions) {
                $('#user-analytics').show();

                var xaxisData = [];
                var coreMemberResponse = [];
                var nonCoreMemberResponse = [];

                for (var i = 0; i < sessions.length; i++) {
                    var session = sessions[i].topic;
                    var coreResponse = sessions[i].coreMemberRating;
                    var nonCoreResponse = sessions[i].nonCoreMemberRating;

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
    }

}
