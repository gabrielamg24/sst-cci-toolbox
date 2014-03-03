from pmonitor import PMonitor

usecase = 'mmsrq'

# TODO for testing only, remove this line when producing
years = ['2003']
months = ['01']
sensors = [('atsr_orb.2', '1995-06-01', '2003-06-22'),
           ('atsr_orb.3', '2002-05-20', '2012-04-08')]
# 300000 leads to about 2500 surviving samples per month
samplespermonth = 300000
skip = 0

# archiving rules
# mms/archive/atsr.3/v2.1/2003/01/17/ATS_TOA_1P...N1
# mms/archive/mms2/smp/atsr.3/2003/atsr.3-smp-2003-01-b.txt
# mms/archive/mms2/sub/atsr.3/2003/atsr.3-sub-2003-01.nc
# mms/archive/mms2/nwp/atsr.3/2003/atsr.3-nwp-2003-01.nc
# mms/archive/mms2/nwp/atsr.3/2003/atsr.3-nwpAn-2003-01.nc
# mms/archive/mms2/nwp/atsr.3/2003/atsr.3-nwpFc-2003-01.nc
# mms/archive/mms2/arc/atsr.3/2003/atsr.3-arc-2003-01.nc
# mms/archive/mms2/mmd/atsr.3/2003/atsr.3-mmd-2003-01.nc

def prev_year_month_of(year, month):
    if month == '02':
        return year, '01'
    elif month == '03':
        return year, '02'
    elif month == '04':
        return year, '03'
    elif month == '05':
        return year, '04'
    elif month == '06':
        return year, '05'
    elif month == '07':
        return year, '06'
    elif month == '08':
        return year, '07'
    elif month == '09':
        return year, '08'
    elif month == '10':
        return year, '09'
    elif month == '11':
        return year, '10'
    elif month == '12':
        return year, '11'
    else:
        return str(int(year) - 1), '12'


def next_year_month_of(year, month):
    if month == '01':
        return year, '02'
    elif month == '02':
        return year, '03'
    elif month == '03':
        return year, '04'
    elif month == '04':
        return year, '05'
    elif month == '05':
        return year, '06'
    elif month == '06':
        return year, '07'
    elif month == '07':
        return year, '08'
    elif month == '08':
        return year, '09'
    elif month == '09':
        return year, '10'
    elif month == '10':
        return year, '11'
    elif month == '11':
        return year, '12'
    else:
        return str(int(year) + 1), '01'


inputs = []
for year in years:
    for month in months:
        inputs.append('/inp/' + year + '/' + month)
# Add fulfilled preconditions for temporal boundary around mission start and end
for (sensor, sensorstart, sensorstop) in sensors:
    if years[0] + '-' + months[0] >= sensorstart:
        prev_month_year, prev_month = prev_year_month_of(years[0], months[0])
    else:
        prev_month_year, prev_month = prev_year_month_of(sensorstart[0:4], sensorstart[5:7])
    inputs.append('/obs/' + prev_month_year + '/' + prev_month)
    if years[-1] + '-' + months[-1] <= sensorstop:
        next_month_year, next_month = next_year_month_of(years[-1], months[-1])
    else:
        next_month_year, next_month = next_year_month_of(sensorstop[0:4], sensorstop[5:7])
    inputs.append('/obs/' + next_month_year + '/' + next_month)

hosts = [('localhost', 1)]
types = [('ingestion-run.sh', 1),
         ('sampling-run.sh', 1),
         ('clearsky-run.sh', 1),
         ('mmd-run.sh', 1),
         ('coincidence-run2.sh', 1),
         ('nwp-run2.sh', 1),
         ('arc-run2.sh', 1),
         ('reingestion-run2.sh', 1)]

pm = PMonitor(inputs,
              request='mmsrq',
              logdir='trace',
              hosts=hosts,
              types=types)

for year in years:
    for month in months:
        # 1. Ingestion of all sensor data required for month
        pm.execute('ingestion-run.sh',
                   ['/inp/' + year + '/' + month],
                   ['/obs/' + year + '/' + month],
                   parameters=[year, month, usecase])
        continue

        for sensor, sensorstart, sensorstop in sensors:
            if year + '-' + month < sensorstart or year + '-' + month > sensorstop:
                continue
            prev_month_year, prev_month = prev_year_month_of(year, month)
            next_month_year, next_month = next_year_month_of(year, month)
            # 2. Generate sampling points per month and sensor
            pm.execute('sampling-run.sh',
                       ['/obs/' + prev_month_year + '/' + prev_month,
                        '/obs/' + year + '/' + month,
                        '/obs/' + next_month_year + '/' + next_month],
                       ['/smp/' + sensor + '/' + prev_month_year + '/' + prev_month,
                        '/smp/' + sensor + '/' + year + '/' + month,
                        '/smp/' + sensor + '/' + next_month_year + '/' + next_month],
                       parameters=[year, month, sensor, str(samplespermonth), str(skip), usecase])
            skip += samplespermonth

            # 3. Remove cloudy sub-scenes, remove overlapping sub-scenes, create matchup entries in database
            pm.execute('clearsky-start.sh',
                       ['/smp/' + sensor + '/' + prev_month_year + '/' + prev_month,
                        '/smp/' + sensor + '/' + year + '/' + month,
                        '/smp/' + sensor + '/' + next_month_year + '/' + next_month],
                       ['/clr/' + sensor + '/' + year + '/' + month],
                       parameters=[year, month, sensor, usecase])
            continue
            # 4. Create single-sensor MMD with subscenes
            pm.execute('mmd-run.sh',
                       ['/clr/' + sensor + '/' + year + '/' + month],
                       ['/sub/' + sensor + '/' + year + '/' + month],
                       parameters=[year, month, sensor, 'sub', usecase])
            # 5. Add coincidences from Sea Ice and Aerosol data
            # TODO - why do not extract and ingest those?
            pm.execute('coincidence-run2.sh',
                       ['/clr/' + sensor + '/' + year + '/' + month],
                       ['/con/' + sensor + '/' + year + '/' + month],
                       parameters=[year, month, sensor, usecase])
            # 6. Extract NWP data for sub-scenes
            pm.execute('nwp-run2.sh',
                       ['/sub/' + sensor + '/' + year + '/' + month],
                       ['/nwp/' + sensor + '/' + year + '/' + month],
                       parameters=[year, month, sensor, usecase])
            # 7. Extract NWP analysis (an) and forecast (fc) data for matchup point
            # TODO - why do I do this? Will these be ingested later?
            pm.execute('nwpmatchup-run2.sh',
                       ['/sub/' + sensor + '/' + year + '/' + month],
                       ['/nwp/' + sensor + '/' + year + '/' + month,
                        '/nwp/' + sensor + '/' + year + '/' + month],
                       parameters=[year, month, sensor, usecase])
            # 8. Conduct ARC processing
            pm.execute('arc-run2.sh',
                       ['/nwp/' + sensor + '/' + year + '/' + month],
                       ['/arc/' + sensor + '/' + year + '/' + month],
                       parameters=[year, month, sensor, usecase])
            # 9. Re-ingest sensor sub-scenes into database
            pm.execute('reingestion-run2.sh',
                       ['/sub/' + sensor + '/' + year + '/' + month],
                       ['/con/' + sensor + '/' + year + '/' + month],
                       parameters=[year, month, sensor, 'sub', usecase])
            # 10. Ingest sensor sub-scene NWP data into database
            # TODO - why don't I ingest NWP analysis and forecast data?
            pm.execute('reingestion-run2.sh',
                       ['/nwp/' + sensor + '/' + year + '/' + month],
                       ['/con/' + sensor + '/' + year + '/' + month],
                       parameters=[year, month, sensor, 'nwp', usecase])
            # 11. Ingest sensor sub-scene ARC results into database
            pm.execute('reingestion-run2.sh',
                       ['/arc/' + sensor + '/' + year + '/' + month],
                       ['/con/' + sensor + '/' + year + '/' + month],
                       parameters=[year, month, sensor, 'arc', usecase])
            # 12. Produce final single-sensor MMD file
            pm.execute('mmd-run.sh',
                       ['/con/' + sensor + '/' + year + '/' + month],
                       ['/mmd/' + sensor + '/' + year + '/' + month],
                       parameters=[year, month, sensor, 'mmd2', usecase])

pm.wait_for_completion()