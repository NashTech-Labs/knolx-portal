$(function () {
    dataFetch(1)
});

function dataFetch(pageNumber) {

    var self = this;
    self.feedbackHeaders = ko.observableArray([]);

    jsRoutes.controllers.FeedbackFormsReportController.renderAllFeedbackReportsJson(pageNumber).ajax(
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

                console.log(values);
                for (var i = 0; i < feedbackReportHeaderList.length; i++) {
                    var url = jsRoutes.controllers.FeedbackFormsReportController.fetchAllResponsesBySessionId(feedbackReportHeaderList[i].sessionId).url;
                    feedbackReportHeaderList[i]["url"] = url;
                }

                self.feedbackHeaders(feedbackReportHeaderList);

                console.log(page+"ggjhj---->"+ pages);
                paginate(page, pages);

                var paginationLinks = document.querySelectorAll('.paginate');

                for (var i = 0; i < paginationLinks.length; i++) {
                    paginationLinks[i].addEventListener('click', function (event) {
                        dataFetch(this.id);
                    });
                }

                function myViewModel(data) {
                    var self = this;
                    self.feedbackHeaders = ko.observableArray(data);
                    console.log(self.feedbackHeaders.length);
                }

                console.log("asdfghj");
                ko.applyBindings(new myViewModel(feedbackReportHeaderList),document.getElementById('report') );

            }
        });
}




/////////////////////////

var EmpViewModel = function () {
    var self = this;

    self.pagesize = ko.observable(4); // The Default size of the Table.
    self.thispage = ko.observable(0); // The current Page.
    self.pagineationEmp = ko.observableArray(); // The declaration for storing the Paginated data.
    self.Employees = ko.observableArray([]); // The declaration holds data in it from the external call.

//The computed declaration for the number of display of records

    self.page = ko.computed(function () {
        //Logic for displaying number of rows in the table
        if (self.pagesize() == "complete") {
            self.pagineationEmp(self.Employees.slice(0));
        } else {
            var pgsize = parseInt(self.pagesize(), 10),
                fisrt = pgsize * self.thispage(),
                last = fisrt + pgsize;

            self.pagineationEmp(self.Employees.slice(fisrt, last));
        }

    }, self);

//The function for the total number of pages
    self.allpages = function () {
        var totpages = self.Employees().length / self.pagesize() || 1;
        return Math.ceil(totpages);
    }

//The function for Next Page
    self.nextpage = function () {
        if (self.thispage() < self.allpages() - 1) {
            self.thispage(this.thispage() + 1);
        }
    }
//The function for Previous Page
    self.previouspage = function () {
        if (self.thispage() > 0) {
            self.thispage(this.thispage() - 1);
        }
    }

//The function to get data from external service (WEB API)
    getemployees();
    function getemployees()
    {
        $.ajax({
            url: "http://localhost:16269/api/EmployeeInfoAPI",
            type: "GET",
            datatype: "json",
            contenttype:"application/json;utf-8"
        }).done(function (resp) {
            self.Employees(resp);
            alert("Success");
        }).fail(function (err) {
            alert("Error!! " + err.status +"  " + err.statusText);
        });
    }
};
ko.applyBindings(new EmpViewModel());
