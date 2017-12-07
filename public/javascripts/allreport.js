$(function () {
    ko.applyBindings(new AllReportsModel(1));

});

function AllReportsModel(pageNumber) {
    var self = this;
    self.feedbackHeaders = ko.observableArray([]);

    dataFetch(pageNumber);

    function dataFetch(pageNumber) {
        jsRoutes.controllers.FeedbackFormsReportController.manageAllFeedbackReports(pageNumber).ajax(
            {
                type: "GET",
                processData: false,
                beforeSend: function (request) {
                    var csrfToken = document.getElementById('csrfToken').value;

                    return request.setRequestHeader('CSRF-Token', csrfToken);
                },
                success: function (values) {

                    var feedbackReportHeaderList = values["feedbackReportHeaderList"];
                    var pages = values["pages"];
                    var page = values["pageNumber"];

                    for (var i = 0; i < feedbackReportHeaderList.length; i++) {
                        var url = jsRoutes.controllers.FeedbackFormsReportController.fetchAllResponsesBySessionId(feedbackReportHeaderList[i].sessionId).url;
                        feedbackReportHeaderList[i]["url"] = url;
                    }
                    self.feedbackHeaders(feedbackReportHeaderList);

                    paginate(page, pages);

                    var paginationLinks = document.querySelectorAll('.paginate');

                    for (var i = 0; i < paginationLinks.length; i++) {
                        paginationLinks[i].addEventListener('click', function (event) {
                            dataFetch(this.id);
                        });
                    }
                }
            });
    }
}
