
import csv
import random
import os
import re

# Paths
COURSES_PATH = 'docs/SCRAPED DATA/REPORT/courses.csv'
LECTURERS_PATH = 'docs/SCRAPED DATA/REPORT/export_lecturers_2026-02-07-02-19.csv'
GROUPS_PATH = 'csv_files/student-groups.csv'
FEATURES_PATH = 'csv_files/features.csv'
ZONES_PATH = 'csv_files/zones.csv'

# Mappings from Course Code Prefix to Student Group Base Name
PREFIX_MAP = {
    'NSC': 'NURS',
    'NURS': 'NURS',
    'MCB': 'NURS', 
    'MBIO': 'NURS',
    'MTH': 'MATH',
    'MATH': 'MATH',
    'STAT': 'MATH',
    'STA': 'MATH',
    'PHY': 'PHYS',
    'PHYS': 'PHYS',
    'CH': 'CHEM',
    'CHEM': 'CHEM',
    'CMP': 'COSC',
    'COSC': 'COSC',
    'CSC': 'COSC',
    'CENG': 'CENG',
    'CPEN': 'CENG',
    'SENG': 'SENG',
    'SWE': 'SENG',
    'ECON': 'ECON',
    'ECN': 'ECON',
    'ENG': 'ENGL',
    'ENGL': 'ENGL',
    'LAW': 'LAW',
    'PUL': 'LAW',
    'PPL': 'LAW',
    'JIL': 'LAW',
    'BUL': 'LAW',
    'MED': 'MED',
    'PHAR': 'PHAR',
    'PCL': 'PHAR',
    'PCG': 'PHAR',
    'PCT': 'PHAR',
    'MC': 'MCOM',
    'MCOM': 'MCOM',
    'MCM': 'MCOM',
    'ACCT': 'ACCT',
    'ACC': 'ACCT',
    'AGRI': 'AGRI',
    'AGR': 'AGRI',
    'BADM': 'BADM',
    'BUS': 'BADM',
    'INFT': 'INFT',
    'REL': 'REL', 
    'GST': 'GST', 
}

def load_column_set(path, col_name, check_header=True):
    values = set()
    rows = []
    if not os.path.exists(path):
        print(f"Warning: {path} not found.")
        return values, rows
    
    with open(path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row.get(col_name):
                values.add(row[col_name].strip())
            rows.append(row)
    return list(values), rows

def load_student_groups_struct(path):
    groups_db = {}
    if not os.path.exists(path):
        return {}
    
    with open(path, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            is_parent = row.get('is_parent', 'F').upper() == 'T'
            if is_parent:
                continue
                
            base_name = row.get('base_name', '').strip().upper()
            try:
                level_str = row.get('level', '').strip()
                level = int(level_str) if level_str.isdigit() else 0
            except:
                level = 0
                
            if not base_name or not level:
                continue
            
            parent = row.get('parent_group_name', '').strip()
            group_ltr = row.get('group', '').strip()
            
            if parent and group_ltr:
                full_name = f"{parent} (GRP {group_ltr})"
            else:
                 full_name = f"{base_name} {level} (GRP {group_ltr})"
            
            full_name = full_name.replace('  ', ' ')

            if level not in groups_db:
                groups_db[level] = {}
            if base_name not in groups_db[level]:
                groups_db[level][base_name] = []
            
            groups_db[level][base_name].append(full_name)
            
    return groups_db

def parse_course_info(code):
    code = code.upper().replace('BU-', '').strip()
    match = re.search(r'(\d+)', code)
    if match:
        num_str = match.group(1)
        if len(num_str) >= 3:
            level = int(num_str[0]) * 100
        else:
            level = 100 
    else:
        level = 100 
        
    match_alpha = re.match(r'([A-Z]+)', code)
    prefix = match_alpha.group(1) if match_alpha else ''
    return prefix, level

def get_target_group(prefix, level, groups_db):
    base_name = PREFIX_MAP.get(prefix, prefix)
    if level in groups_db and base_name in groups_db[level]:
        return groups_db[level][base_name]
    if level in groups_db:
        for b_name in groups_db[level]:
            if prefix in b_name or b_name in prefix:
                 return groups_db[level][b_name]
    if level >= 500:
         if 500 in groups_db and base_name in groups_db[500]: return groups_db[500][base_name]
         if 400 in groups_db and base_name in groups_db[400]: return groups_db[400][base_name]
    return []

def main():
    valid_emails, _ = load_column_set(LECTURERS_PATH, 'email')
    valid_features, _ = load_column_set(FEATURES_PATH, 'name')
    valid_zones, _ = load_column_set(ZONES_PATH, 'name')
    
    groups_db = load_student_groups_struct(GROUPS_PATH)
    all_valid_groups = []
    for lvl in groups_db:
        for bn in groups_db[lvl]:
            all_valid_groups.extend(groups_db[lvl][bn])

    print(f"Loaded {len(valid_emails)} lecturers, {len(all_valid_groups)} groups, {len(valid_features)} features.")

    if not valid_emails:
        print("Error: No valid lecturers found.")
        return

    # Aggregation Map: Code -> Data
    aggregated_courses = {}
    fieldnames = []
    
    with open(COURSES_PATH, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        fieldnames = reader.fieldnames
        
        for row in reader:
            code = row.get('code', '')
            
            # --- APPLY FIXES FIRST ---
            
            # Fix Email
            curr_email = row.get('lecturer_email', '').strip()
            if curr_email not in valid_emails:
                row['lecturer_email'] = random.choice(valid_emails)

            # Fix Group
            curr_grp = row.get('student_group_names', '').strip()
            if curr_grp not in all_valid_groups:
                prefix, level = parse_course_info(code)
                target_subgroups = get_target_group(prefix, level, groups_db)
                if target_subgroups:
                    row['student_group_names'] = random.choice(target_subgroups)
                else:
                    candidates = [g for g in all_valid_groups if str(level) in g]
                    if candidates:
                        row['student_group_names'] = random.choice(candidates)
                    elif all_valid_groups:
                        row['student_group_names'] = random.choice(all_valid_groups)
                        
            # Fix Features
            curr_feats = [x.strip() for x in row.get('required_features', '').split('|') if x.strip()]
            is_valid_feat = curr_feats and all(f in valid_features for f in curr_feats)
            if not is_valid_feat and valid_features:
                num_features = random.choices([1, 2, 3], weights=[0.6, 0.3, 0.1])[0]
                selected_feats = random.sample(valid_features, k=min(num_features, len(valid_features)))
                row['required_features'] = '|'.join(selected_feats)
                
            # Fix Zones
            if row.get('allowed_zones', '').strip() not in valid_zones:
                row['allowed_zones'] = random.choice(valid_zones) if valid_zones else ''
                
            # --- AGGREGATE ---
            if code not in aggregated_courses:
                aggregated_courses[code] = {
                    'row': row,
                    'groups': set(),
                    'features': set(),
                    'zones': set(),
                    'lecturers': set()
                }
            
            # Merge
            c_grps = row.get('student_group_names', '').split('|')
            for g in c_grps: 
                if g.strip(): aggregated_courses[code]['groups'].add(g.strip())
                
            c_fts = row.get('required_features', '').split('|')
            for f in c_fts: 
                if f.strip(): aggregated_courses[code]['features'].add(f.strip())
                
            c_zns = row.get('allowed_zones', '').split('|')
            for z in c_zns: 
                if z.strip(): aggregated_courses[code]['zones'].add(z.strip())
                
            l_email = row.get('lecturer_email', '').strip()
            if l_email: aggregated_courses[code]['lecturers'].add(l_email)

    # Reconstruct Final Output
    final_rows = []
    # Sort by code for tidiness
    for code in sorted(aggregated_courses.keys()):
        data = aggregated_courses[code]
        base_row = data['row']
        
        base_row['student_group_names'] = '|'.join(sorted(list(data['groups'])))
        base_row['required_features'] = '|'.join(sorted(list(data['features'])))
        base_row['allowed_zones'] = '|'.join(sorted(list(data['zones'])))
        
        # Pick one lecturer (any valid one seen)
        if data['lecturers']:
            base_row['lecturer_email'] = list(data['lecturers'])[0]
            
        final_rows.append(base_row)

    with open(COURSES_PATH, 'w', encoding='utf-8', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(final_rows)
        
    print(f"Updated {COURSES_PATH}. Collapsed from read rows to {len(final_rows)} unique courses.")

if __name__ == '__main__':
    main()
