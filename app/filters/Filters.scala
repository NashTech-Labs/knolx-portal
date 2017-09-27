package filters

import javax.inject.Inject

import play.api.http.DefaultHttpFilters
import play.filters.csrf.CSRFFilter

class Filters @Inject()(csrfFilter: CSRFFilter) extends DefaultHttpFilters(csrfFilter)