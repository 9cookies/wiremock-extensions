# Keywords

## Additional Features

To get even more generic responses that help better testing the wiremock-extensions provide some key words that start with an exclamation mark `!`.

### Random value key words

Generating random integer for a response property
```JSON
{ "id": "$(!Random)" }
```

Generating random UUID for a response property
```JSON
{ "uuid": "$(!UUID)" }
```

Note that multiple occurrences of `$(!Random)` or `$(!UUID)` will result in injecting the same value for all properties. If this is not the desired behavior it is possible to add a arbitrary suffix to the `Random` or `UUID` keyword to get different values for different properties injected.
```JSON
{
  "id": "$(!RandomId)",
  "otherId": "$(!RandomOther)",
  "userId": "$(!UUID.User)",
  "ownerId": "$(!UUID.Owner)"
}
```
However, same suffixes result in same values for different properties. The following example shows the reuse of a specific random value for owner and creator id, but with a different id for modifier.
```JSON
{
  "id": "$(!Random)",
  "ownerId": "$(!UUID.Owner)",
  "creatorId": "$(!UUID.Owner)",
  "modifierId": "$(!UUID.Modifier)"
}
```

For the random integer it is also possible to specify a maximum or minimum and maximum value by providing integers. Spaces will be ignored. If only maximum is provided the value must be positive.
```JSON
{
  "number_between_zero_and_10": "$(!Random[10])",
  "number_between_10_and_100": "$(!Random[10,100])",
  "number_between_15_and_30": "$(!Random[15, 30])",
  "number_between_20_and_50": "$(!Random[20 , 50])",
  "number_between_2_and_5": "$(!Random[2 ,5])"
}
```

### Instant, date time and time stamp calculation

Generating current time stamp in [ISO-8601](https://en.wikipedia.org/wiki/ISO_8601) UTC format for a response property
```JSON
{ "created": "$(!Instant)" }
```

Generating current time stamp in [ISO-8601](https://en.wikipedia.org/wiki/ISO_8601#Time_offsets_from_UTC) offset format for a response property
```JSON
{ "created": "$(!OffsetDateTime)" }
```

Generating current time stamp in [Unix Epoch](https://en.wikipedia.org/wiki/Unix_time) UTC format for a response property
```JSON
{ "created": "$(!Timestamp)" }
```

Generating computed time stamp for a response property using the response pattern `$(!Instant.plus[UNITAMOUNT])` or `$(!OffsetDateTime.plus[UNITAMOUNT])` for [ISO-8601](https://en.wikipedia.org/wiki/ISO_8601) or `$(!Timestamp.plus[UNITAMOUNT])` for [Unix Epoch](https://en.wikipedia.org/wiki/Unix_time) format where `UNIT` indicates the time unit and `AMOUNT` the amount to add or subtract. Valid units are `s`, `m` and `h` for seconds, minutes and hours. Units are case insensitive. Amount might be positive or negative depending on whether the desired result should be in the past (negative) or in the future (positive).
```JSON
{
    "one_second_in_future": "$(!Instant.plus[s1])",
    "one_second_in_past": "$(!Instant.plus[s-1])",
    "one_minute_in_future":  "$(!OffsetDateTime.plus[m1])",
    "one_minute_in_past":  "$(!OffsetDateTime.plus[m-1])",
    "one_hour_in_future":  "$(!Timestamp.plus[h1])",
    "one_hour_in_past":  "$(!Timestamp.plus[h-1])"
}
```

Note that all time stamps are returned in UTC format except offset date times which use the systems default time zone as offset.

### Environment variable key word

In addition it is possible to access environment variables if the variable doesn't exist `null` will be used as replacement.
```JSON
{ "configured": "$(!ENV[MY_ENV_KEY])" }
```
