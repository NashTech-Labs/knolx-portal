function paginate(pageNumber, pages) {
    var pagination = "";

    if (pages >= pageNumber) {
        switch (true) {
            case (pages > 5 && pageNumber < 5) : {
                pagination += reducedFromIterator(1, 5, pageNumber, 0);
                pagination += dots();
                pagination += inActiveList(pages);
                break;
            }
            case (pages <= 5 && pageNumber <= 5) :
                pagination += reducedFromIterator(1, pages, pageNumber, 0);
                break;
            case (pages > 5 && pageNumber >= 5 && pages == pageNumber) : {
                pagination += reverseIterator(5, 1, pageNumber, pages);
                pagination += dots();
                pagination += pageNumber == pages ? activeList(pages) : inActiveList(pages);
                break;
            }
            case (pages > 5 && pageNumber >= 5 && pageNumber < pages - 2)  : {
                pagination += reverseIterator(2, 0, pageNumber, pageNumber);
                for (var iterator = 1; iterator <= 2; iterator++) {
                    pagination += fromIterator(pageNumber, (pageNumber + iterator));
                }
                pagination += dots();
                pagination += inActiveList(pages);
                break;
            }
            case (pages > 5 && pageNumber >= 5 && pageNumber == pages - 2)      : {
                pagination += reverseIterator(3, 0, pageNumber, pageNumber);
                pagination += pageNumber == (pageNumber + 1) ? activeList((pageNumber + 1)) : inActiveList((pageNumber + 1));
                pagination += dots();
                pagination += inActiveList(pages);
                break;
            }
            case (pages > 5 && pageNumber >= 5 && pageNumber > pages - 2 && pageNumber != pages) : {
                pagination += reverseIterator(4, 0, pageNumber, pageNumber);
                pagination += dots();
                pagination += inActiveList(pages);
                break;
            }
        }
    }

    $('.pagination').html(pagination);
}

function activeList(value) {
    return "<li class='active'><a  class='paginate' id='" + value + "'>" + value + "</a></li>"
}

function inActiveList(value) {
    return "<li class='inactive'><a  class='paginate' id='" + value + "'>" + value + "</a></li>"
}

function dots() {
    return "<li class='inactive'><a>...</a></li>";
}

function reverseIterator(from, till, pageNumber, compareWith) {
    var pagination = "";

    for (var iterator = from; iterator >= till; iterator--) {
        pagination += pageNumber == (compareWith - iterator) ? activeList((compareWith - iterator)) : inActiveList((compareWith - iterator));
    }

    return pagination;
}

function fromIterator(pageNumber, compareWith) {
    var pagination = "";
    pagination += pageNumber == compareWith ? activeList(compareWith) : inActiveList(compareWith);
    return pagination;
}

function reducedFromIterator(from, till, pageNumber, offset) {
    var pagination = "";

    for (var iterator = from; iterator <= till; iterator++) {
        pagination += fromIterator(pageNumber, (offset + iterator));
    }

    return pagination;
}
