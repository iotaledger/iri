import datetime


class TagGenerator(object):

    TAG_YEAR = {
        2016: b"B9AF",
        2017: b"B9AG",
        2018: b"B9AH",
        2019: b"B9AI",
        2020: b"B9AH",
    }
    TAG_MONTH = [
        b"99",                              # 00
        b"9A", b"9B", b"9C", b"9D", b"9E",  # 01-05
        b"9F", b"9G", b"9H", b"9I", b"A9",  # 06-10
        b"AA", b"AB"                        # 11-12
    ]
    TAG_DAY = [
        b"99",                              # 00
        b"9A", b"9B", b"9C", b"9D", b"9E",  # 01-05
        b"9F", b"9G", b"9H", b"9I", b"A9",  # 06-10
        b"AA", b"AB", b"AC", b"AD", b"AE",  # 11-15
        b"AF", b"AG", b"AH", b"AI", b"B9",  # 16-20
        b"BA", b"BB", b"BC", b"BD", b"BE",  # 21-25
        b"BF", b"BG", b"BH", b"BI", b"C9",  # 26-30
        b"CA",                              # 31
    ]

    @classmethod
    def get_current_tag(cls, txn_type=""):
        today = datetime.date.today()
        return TagGenerator.TAG_YEAR[today.year] + \
            TagGenerator.TAG_MONTH[today.month] + \
            TagGenerator.TAG_DAY[today.day] + txn_type

    @classmethod
    def get_previous_tag(cls, txn_type=""):
        today = datetime.date.today()
        one_day = datetime.timedelta(days=1)
        yesterday = today - one_day
        return TagGenerator.TAG_YEAR[yesterday.year] + \
            TagGenerator.TAG_MONTH[yesterday.month] + \
            TagGenerator.TAG_DAY[yesterday.day] + txn_type

