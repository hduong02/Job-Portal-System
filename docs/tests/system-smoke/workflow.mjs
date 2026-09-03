import { Blocked, mailMatches, shortlist } from './lib.mjs';

export async function executeWorkflow(run) {
  const { config: cfg, report } = run;
  const marker = report.runId;
  const f = {};
  const get = (path, actor = 'candidate') => run.json('GET', path, { actor });
  const mutate = (method, path, actor, body) => run.json(method, path, { actor, body });
  const contains = (page, id) => Array.isArray(page?.content) && page.content.some(item => item.id === id);
  const positive = value => Number.isSafeInteger(value) && value > 0;
  const remove = async (resource, actor) => {
    const ack = await mutate('DELETE', resource.path, actor);
    run.check('Deletion acknowledged', ack?.status === true);
    const items = await get(resource.path.slice(0, resource.path.lastIndexOf('/')), actor);
    run.check('Removed child absent from list', !(Array.isArray(items) ? items : items.content || []).some(item => item.id === resource.id));
    resource.cleanup = 'DELETED';
    await run.persist();
  };
  const security = (name, method, path, options, ready = true) => run.stage(`Security: ${name}`, async () => {
    const response = await run.request(method, path, { ...options, probe: true });
    // Negative creation checks must also clean up a record returned by a buggy successful response.
    if (method === 'POST' && path === '/api/applications') {
      if (positive(response.json?.id) && !report.resources.some(item => item.kind === 'application' && item.id === response.json.id))
        await run.remember('application', response.json.id, `${path}/${response.json.id}`, options.actor,
          { jobId: options.body?.jobId, resumeId: options.body?.resumeId });
      else if (response.status >= 200 && response.status < 300 && !positive(response.json?.id))
        report.uncertainMutations.push({ method, path, actor: options.actor, reason: 'Unexpected successful negative creation without an ID' });
    }
    run.check(`${name}: expected HTTP ${options.expected.join('/')}`, options.expected.includes(response.status), `actual HTTP ${response.status}`);
    run.check(`${name}: valid JSON`, response.json !== undefined);
  }, ready);

  try {
    await run.stage('Gateway readiness', async () => {
      await run.poll('Gateway health is UP', async remaining => {
        try { return await run.request('GET', '/actuator/health', { probe: true, timeoutMs: Math.min(cfg.requestMs, remaining) }); }
        catch { return null; }
      }, value => value?.status === 200 && value.json?.status === 'UP', cfg.readinessMs);
    });

    await run.stage('Mailbox access', async () => {
      const response = await run.request('GET', '/api/v2/messages?start=0&limit=1', { mailbox: true, probe: true });
      if (response.status !== 200 || !Array.isArray(response.json?.items))
        throw new Blocked('MAILHOG_URL must reach the VPS MailHog API (use a private tunnel)');
      f.mailbox = true;
    });

    await run.stage('Existing administrator login', async () => {
      const response = await run.request('POST', '/auth/login', { probe: true,
        body: { email: cfg.adminEmail, password: cfg.adminPassword } });
      if (response.status !== 200 || !response.json?.jwt || response.json?.user?.role !== 'ROLE_ADMIN')
        throw new Blocked('An existing active ROLE_ADMIN account is required');
      run.actors.admin = { jwt: response.json.jwt, ...response.json.user };
    });

    // Do not create data if either required external prerequisite is unavailable.
    if (!run.actors.admin || !f.mailbox) {
      await run.stage('Recruitment journey', async () => {}, false);
      return;
    }

    for (const [name, role] of [['candidate', 'ROLE_JOB_SEEKER'], ['employer', 'ROLE_EMPLOYER'],
      ['otherCandidate', 'ROLE_JOB_SEEKER'], ['otherEmployer', 'ROLE_EMPLOYER']]) {
      await run.stage(`Register and login ${name}`, async () => {
        const email = `${marker}.${name.toLowerCase()}@${cfg.emailDomain}`;
        const response = await run.request('POST', '/auth/signup', { probe: true,
          body: { email, password: cfg.smokePassword, fullName: `Smoke ${name}`, role } });
        const auth = response.json;
        if (positive(auth?.user?.id)) {
          run.actors[name] = { ...auth.user, email, jwt: auth.jwt };
          await run.remember('user', auth.user.id, `/api/users/${auth.user.id}`, 'admin', { email });
        } else if (response.status >= 200 && response.status < 300) {
          report.uncertainMutations.push({ method: 'POST', path: '/auth/signup', email, reason: 'Signup succeeded without a usable user ID' });
        }
        run.check('Signup returns HTTP 200 and expected user', response.status === 200 && auth?.user?.email === email && auth.user.role === role);
        run.check('Signup returns JWT', typeof auth.jwt === 'string' && auth.jwt.length > 0);
        const login = await mutate('POST', '/auth/login', undefined, { email, password: cfg.smokePassword });
        run.check('Login returns same identity and JWT', login.user?.id === auth.user.id && typeof login.jwt === 'string' && login.jwt.length > 0);
        run.actors[name].jwt = login.jwt;
        const profile = await get('/api/users/profile', name);
        run.check('Profile matches registered user', profile.id === auth.user.id && profile.role === role);
      });
    }

    await run.stage('Profiles persist updates', async () => {
      for (const actor of ['candidate', 'employer']) {
        await mutate('PUT', '/api/users/profile', actor, { fullName: `Smoke ${actor} updated`, phone: '+66000000000' });
        const profile = await get('/api/users/profile', actor);
        run.check(`${actor} profile update persisted`, profile.fullName === `Smoke ${actor} updated` && profile.phone === '+66000000000');
      }
    }, Boolean(run.actors.candidate?.jwt && run.actors.employer?.jwt));

    await run.stage('Company creation and verification', async () => {
      f.companyBody = { name: `Smoke Company ${marker}`, tagline: 'Disposable smoke fixture', description: marker,
        companySize: 'SMALL', companyType: 'STARTUP', industryType: 'TECHNOLOGY',
        email: run.actors.employer.email, website: 'https://example.com', foundedYear: 2020,
        registrationNumber: marker, socialLinks: [{ platform: 'GITHUB', url: 'https://github.com/example' }] };
      f.company = await run.create('company', '/api/companies', 'employer', f.companyBody, [201]);
      run.check('Company belongs to employer', f.company.ownerId === run.actors.employer.id);
      await mutate('PATCH', `/api/companies/${f.company.id}/verify`, 'admin');
      f.company = await get(`/api/companies/${f.company.id}`, 'employer');
      run.check('Company verification persisted as ACTIVE', f.company.status === 'ACTIVE');
      run.check('Owner company lookup', (await get('/api/companies/my', 'employer')).id === f.company.id);
    }, Boolean(run.actors.employer?.jwt));

    await run.stage('Job metadata', async () => {
      f.category = await run.create('category', '/api/job-categories', 'admin', { name: `Software ${marker}`, description: marker }, [201]);
      f.skill = await run.create('skill', '/api/job-skills', 'admin', { name: `Java ${marker}`, category: 'PROGRAMMING_LANGUAGE' }, [201]);
      f.tag = await run.create('tag', '/api/job-tags', 'admin', { name: `Smoke ${marker}` }, [201]);
      for (const [kind, path] of [['category', 'job-categories'], ['skill', 'job-skills'], ['tag', 'job-tags']]) {
        const items = await get(`/api/${path}`, 'admin');
        run.check(`${kind} appears in metadata list`, Array.isArray(items) && items.some(item => item.id === f[kind].id));
      }
    });

    await run.stage('Job create edit publish', async () => {
      const future = new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 10);
      f.jobBody = { title: `Java Engineer ${marker}`, description: 'Build Java REST APIs with Spring Boot and PostgreSQL.',
        requirements: 'Java, Spring Boot, SQL', responsibilities: 'Design and test backend services.', categoryId: f.category.id,
        skillIds: [f.skill.id], tagIds: [f.tag.id], city: 'Bangkok', country: 'Thailand',
        minSalary: 1000, maxSalary: 2000, jobType: 'FULL_TIME', workMode: 'REMOTE', experienceLevel: 'MID_LEVEL',
        openings: 1, applicationDeadline: future, expiresAt: future };
      f.job = await run.create('job', '/api/jobs', 'employer', f.jobBody, [201]);
      report.resources.find(item => item.kind === 'job' && item.id === f.job.id).companyId = f.company.id;
      run.check('Job company and employer match', f.job.company?.id === f.company.id && f.job.employerId === run.actors.employer.id);
      f.jobBody.description += ' Updated smoke description.';
      await mutate('PUT', `/api/jobs/${f.job.id}`, 'employer', f.jobBody);
      await mutate('PATCH', `/api/jobs/${f.job.id}/publish`, 'employer');
      f.job = await get(`/api/jobs/${f.job.id}`);
      run.check('Published job is active and OPEN', f.job.status === 'OPEN' && f.job.active === true);
      run.check('Job metadata persisted', f.job.category?.id === f.category.id && f.job.skills?.some(skill => skill.id === f.skill.id)
        && f.job.tags?.some(tag => tag.id === f.tag.id));
    }, Boolean(f.company?.status === 'ACTIVE' && f.category && f.skill && f.tag && run.actors.candidate?.jwt));

    await run.stage('Search and cache-visible updates', async () => {
      const query = new URLSearchParams({ keyword: marker, companyId: f.company.id, categoryId: f.category.id,
        skillIds: f.skill.id, tagIds: f.tag.id, status: 'OPEN', workMode: 'REMOTE', page: '0', size: '20' });
      const results = await get(`/api/jobs?${query}`);
      run.check('Filtered search returns exact fixture once', Array.isArray(results.content)
        && results.content.filter(item => item.id === f.job.id).length === 1);
      for (let i = 0; i < 2; i++) {
        run.check('Repeated job detail has expected ID', (await get(`/api/jobs/${f.job.id}`)).id === f.job.id);
        run.check('Repeated company detail has expected ID', (await get(`/api/companies/${f.company.id}`)).id === f.company.id);
      }
      f.companyBody.tagline = `Updated ${marker}`;
      await mutate('PUT', `/api/companies/${f.company.id}`, 'employer', f.companyBody);
      run.check('Company update visible after warm reads', (await get(`/api/companies/${f.company.id}`)).tagline === f.companyBody.tagline);
      f.jobBody.description += ' Cache invalidation verified.';
      await mutate('PUT', `/api/jobs/${f.job.id}`, 'employer', f.jobBody);
      run.check('Job update visible after warm reads', (await get(`/api/jobs/${f.job.id}`)).description === f.jobBody.description);
    }, Boolean(f.job?.status === 'OPEN'));

    for (const actor of ['candidate', 'otherCandidate']) {
      await run.stage(`Resume builder ${actor}`, async () => {
        const resume = await run.create('resume', '/api/resumes', actor,
          { title: `Smoke Resume ${marker} ${actor}`, template: 'CLASSIC', visibility: 'PRIVATE', isDefault: false });
        f[`${actor}Resume`] = resume;
        run.check('Resume owner and visibility match', resume.candidateId === run.actors[actor].id && resume.visibility === 'PRIVATE');
        const base = `/api/resumes/${resume.id}`;
        await mutate('PUT', `${base}/personal-info`, actor, { firstName: 'Smoke', lastName: actor,
          email: run.actors[actor].email, headline: 'Java Engineer', city: 'Bangkok', country: 'Thailand' });
        await mutate('PATCH', `${base}/summary?${new URLSearchParams({ summary: 'Java developer building Spring Boot services.' })}`, actor);
        for (const [kind, suffix, body] of [
          ['education', 'educations', { institutionName: 'Smoke University', degree: 'Bachelor', fieldOfStudy: 'Computer Science', startDate: '2018-01-01', endDate: '2022-01-01' }],
          ['experience', 'work-experiences', { companyName: 'Smoke Previous Company', jobTitle: 'Java Developer', startDate: '2022-02-01', isCurrentJob: true, employmentType: 'FULL_TIME', description: 'Built Spring Boot APIs.', technologies: ['Java', 'PostgreSQL'] }],
          ['resumeSkill', 'skills', { skillName: 'Java', proficiencyLevel: 'ADVANCED', yearsOfExperience: 4 }],
          ['project', 'projects', { title: 'Smoke API', description: 'Spring Boot REST API', technologies: ['Java'], projectUrl: 'https://example.com' }],
          ['language', 'languages', { languageName: 'English', proficiency: 'PROFESSIONAL' }],
        ]) await run.create(kind, `${base}/${suffix}`, actor, body, kind === 'resumeSkill' ? [201] : [200]);
        await mutate('PATCH', `${base}/set-default`, actor);
        const stored = await get(base, actor);
        run.check('Resume default and personal information persisted', stored.isDefault === true && stored.personalInfo?.email === run.actors[actor].email);
        run.check('Resume summary persisted', stored.summary === 'Java developer building Spring Boot services.');
        for (const field of ['educations', 'workExperiences', 'skills', 'projects', 'languages'])
          run.check(`${field} persisted`, Array.isArray(stored[field]) && stored[field].length === 1);
      }, Boolean(run.actors[actor]?.jwt));
    }

    await run.stage('Saved jobs', async () => {
      const saved = await run.create('savedJob', '/api/preferences/saved-jobs', 'candidate', { jobId: f.job.id, note: marker });
      run.check('Saved job linked to fixture and candidate', saved.jobId === f.job.id && saved.candidateId === run.actors.candidate.id);
      run.check('Saved job listed', contains(await get('/api/preferences/saved-jobs'), saved.id));
      run.check('Saved check is true', await get(`/api/preferences/saved-jobs/check?jobId=${f.job.id}`) === true);
      await remove(report.resources.find(item => item.kind === 'savedJob' && item.id === saved.id), 'candidate');
      run.check('Saved check is false after removal', await get(`/api/preferences/saved-jobs/check?jobId=${f.job.id}`) === false);
    }, Boolean(f.job?.status === 'OPEN' && run.actors.candidate?.jwt));

    await run.stage('Candidate applies and employer reads', async () => {
      f.applicationBody = { jobId: f.job.id, resumeId: f.candidateResume.id, coverLetter: marker, expectedSalary: 1500,
        availableFrom: new Date(Date.now() + 86400000).toISOString().slice(0, 10) };
      f.application = await run.create('application', '/api/applications', 'candidate', f.applicationBody);
      Object.assign(report.resources.find(item => item.kind === 'application' && item.id === f.application.id),
        { jobId: f.job.id, resumeId: f.candidateResume.id });
      report.retained.push({ kind: 'screening-work', applicationId: f.application.id,
        reason: 'Screening is asynchronous and has no cancel/delete API; a row may remain even after cleanup' });
      await run.persist();
      run.check('Application relationships and initial status', f.application.candidate?.id === run.actors.candidate.id
        && f.application.job?.id === f.job.id && f.application.resumeId === f.candidateResume.id && f.application.status === 'PENDING'
        && f.application.coverLetter === marker && Number(f.application.expectedSalary) === 1500
        && f.application.availableFrom === f.applicationBody.availableFrom);
      run.check('Candidate application listed once', (await get('/api/applications/my')).content.filter(item => item.id === f.application.id).length === 1);
      run.check('Employer job application listed', contains(await get(`/api/applications/job/${f.job.id}`, 'employer'), f.application.id));
      run.check('Employer sees application detail', (await get(`/api/applications/${f.application.id}`, 'employer')).candidate?.id === run.actors.candidate.id);
    }, Boolean(f.job?.status === 'OPEN' && f.candidateResume));

    await run.stage('Live AI summary', async () => {
      const value = await run.json('POST', '/api/ai/resume/summary', { actor: 'candidate', timeoutMs: cfg.aiMs,
        body: { targetJobTitle: 'Java Engineer', yearOfExperience: 4, skills: ['Java', 'Spring Boot', 'PostgreSQL'],
          workExperiences: [{ jobTitle: 'Java Developer', company: 'Smoke Company', description: 'Built REST APIs' }],
          educations: [{ degree: 'Bachelor', fieldOfStudy: 'Computer Science', institutionName: 'Smoke University' }] } });
      run.check('Gemini returns nonempty generated content', typeof value.content === 'string' && value.content.trim().length > 0);
    }, Boolean(run.actors.candidate?.jwt));

    await run.stage('Live asynchronous screening', async () => {
      const stored = await run.poll('Application screening completes', remaining => run.json('GET', `/api/applications/${f.application.id}`,
        { actor: 'employer', timeoutMs: Math.min(cfg.requestMs, remaining) }), value => value?.screening != null);
      const screening = stored.screening;
      if (positive(screening.id)) report.retained.push({ kind: 'screening', id: screening.id, applicationId: f.application.id,
        reason: 'No screening deletion API exists; application deletion does not cascade to screening' });
      for (const field of ['overallScore', 'skillsMatchScore', 'experienceMatchScore', 'educationMatchScore'])
        run.check(`${field} is an integer in 0–100`, Number.isInteger(screening[field]) && screening[field] >= 0 && screening[field] <= 100);
      run.check('AI shortlist agrees with score', stored.aiScore === screening.overallScore
        && stored.aiShortListStatus === shortlist(stored.aiScore) && screening.shortListStatus === stored.aiShortListStatus,
        `application score=${stored.aiScore}, shortlist=${stored.aiShortListStatus}; screening score=${screening.overallScore}, shortlist=${screening.shortListStatus}`);
      for (const field of ['matchedSkills', 'missingSkills', 'strengths', 'concerns'])
        run.check(`${field} is a string array`, Array.isArray(screening[field]) && screening[field].every(item => typeof item === 'string'));
      run.check('Screening summary and identity present', positive(screening.id) && typeof screening.summary === 'string' && screening.summary.trim().length > 0);
    }, Boolean(f.application));

    await run.stage('Employer star notes and filters', async () => {
      const base = `/api/applications/${f.application.id}`;
      run.check('Employer stars application', (await mutate('PATCH', `${base}/star`, 'employer')).isStarred === true);
      run.check('Starred company filter finds application', contains(await get(`/api/applications/company?jobId=${f.job.id}&status=PENDING&isStarred=true`, 'employer'), f.application.id));
      const note = await run.create('note', `${base}/notes`, 'employer', { content: marker }, [201]);
      run.check('Employer note content stored', (await get(`${base}/notes`, 'employer')).some(item => item.id === note.id && item.content === marker));
      await remove(report.resources.find(item => item.kind === 'note' && item.id === note.id), 'employer');
    }, Boolean(f.application));

    await run.stage('Employer shortlists and status persists', async () => {
      const patch = await mutate('PATCH', `/api/applications/${f.application.id}/status`, 'employer', { status: 'SHORTLISTED' });
      run.check('Status PATCH is compact', patch.id === f.application.id && patch.status === 'SHORTLISTED' && Boolean(patch.updatedAt)
        && !('job' in patch) && !('candidate' in patch) && !('notes' in patch));
      run.check('Candidate sees persisted shortlist', (await get(`/api/applications/${f.application.id}`)).status === 'SHORTLISTED');
      f.shortlisted = true;
    }, Boolean(f.application));

    await run.stage('Kafka to captured email', async () => {
      const query = new URLSearchParams({ kind: 'to', query: run.actors.candidate.email, start: '0', limit: '50' });
      const message = await run.poll('Shortlist email delivered', async remaining => {
        const response = await run.json('GET', `/api/v2/search?${query}`, { mailbox: true, timeoutMs: Math.min(cfg.requestMs, remaining) });
        run.check('MailHog search returns items', Array.isArray(response.items));
        return response.items.find(item => mailMatches(item, { email: run.actors.candidate.email, title: f.jobBody.title, company: f.companyBody.name }));
      }, Boolean);
      report.retained.push({ kind: 'email', id: message.ID, reason: 'Run-specific captured status email; mailbox is not purged' });
    }, Boolean(f.shortlisted));

    // Every negative check is isolated so a discovered defect does not hide the rest of the diagnostics.
    for (const [method, path] of [['GET', '/api/users/profile'], ['GET', '/api/companies'], ['GET', '/api/jobs'],
      ['GET', '/api/resumes/my'], ['GET', '/api/applications/my'], ['GET', '/api/preferences/saved-jobs'], ['POST', '/api/ai/resume/summary']])
      await security(`Missing JWT ${path}`, method, path, { expected: [401], ...(method === 'POST' ? { body: {} } : {}) });
    await security('Malformed JWT', 'GET', '/api/users/profile', { headers: { Authorization: 'Bearer smoke-invalid' }, expected: [401] });
    await security('Invalid login', 'POST', '/auth/login', { body: { email: run.actors.candidate?.email, password: `wrong-${marker}` }, expected: [400, 401, 403] }, Boolean(run.actors.candidate));
    await security('Invalid required application fields', 'POST', '/api/applications', { actor: 'candidate', body: {}, expected: [400, 422] }, Boolean(run.actors.candidate?.jwt));
    await security('Duplicate application', 'POST', '/api/applications', { actor: 'candidate', body: f.applicationBody, expected: [400, 409, 422] }, Boolean(f.application));
    const privateResume = f.candidateResume && `/api/resumes/${f.candidateResume.id}`;
    await security('Other candidate reads private resume', 'GET', privateResume, { actor: 'otherCandidate', expected: [403, 404] }, Boolean(privateResume && run.actors.otherCandidate?.jwt));
    await security('Other candidate edits private resume', 'PATCH', `${privateResume}/summary?summary=Unauthorized`, { actor: 'otherCandidate', expected: [403, 404] }, Boolean(privateResume && run.actors.otherCandidate?.jwt));
    await security('Other candidate reads application', 'GET', `/api/applications/${f.application?.id}`, { actor: 'otherCandidate', expected: [403, 404] }, Boolean(f.application && run.actors.otherCandidate?.jwt));
    await security('Other candidate withdraws application', 'PATCH', `/api/applications/${f.application?.id}/withdraw`, { actor: 'otherCandidate', body: { reason: 'Unauthorized' }, expected: [403, 404] }, Boolean(f.application && run.actors.otherCandidate?.jwt));
    await security('Other employer edits company', 'PUT', `/api/companies/${f.company?.id}`, { actor: 'otherEmployer', body: f.companyBody, expected: [403, 404] }, Boolean(f.company && run.actors.otherEmployer?.jwt));
    await security('Other employer edits job', 'PUT', `/api/jobs/${f.job?.id}`, { actor: 'otherEmployer', body: f.jobBody, expected: [403, 404] }, Boolean(f.job && run.actors.otherEmployer?.jwt));
    await security('Other employer changes application status', 'PATCH', `/api/applications/${f.application?.id}/status`, { actor: 'otherEmployer', body: { status: 'SHORTLISTED' }, expected: [403, 404] }, Boolean(f.application && run.actors.otherEmployer?.jwt));
    await security('Non-admin verifies company with forged role', 'PATCH', `/api/companies/${f.company?.id}/verify`, { actor: 'candidate', headers: { 'X-User-Role': 'ROLE_ADMIN' }, expected: [403] }, Boolean(f.company && run.actors.candidate?.jwt));
    await security('Non-admin deactivates company', 'PATCH', `/api/companies/${f.company?.id}/deactivate`, { actor: 'candidate', expected: [403] }, Boolean(f.company && run.actors.candidate?.jwt));
    // A successful buggy response only changes an account created by this run.
    await security('Non-admin suspends user', 'PATCH', `/api/users/${run.actors.otherCandidate?.id}/suspend`, { actor: 'candidate', expected: [403] }, Boolean(run.actors.otherCandidate && run.actors.candidate?.jwt));
    await security('Non-admin activates user', 'PATCH', `/api/users/${run.actors.otherCandidate?.id}/activate`, { actor: 'candidate', expected: [403] }, Boolean(run.actors.otherCandidate && run.actors.candidate?.jwt));
    await security('Non-admin lists users', 'GET', '/api/users?page=0&size=1', { actor: 'candidate', expected: [403] }, Boolean(run.actors.candidate?.jwt));
    await security('Other candidate reads private resume education', 'GET', `${privateResume}/educations`, { actor: 'otherCandidate', expected: [403, 404] }, Boolean(privateResume && run.actors.otherCandidate?.jwt));
    for (const [kind, path, body] of [
      ['category', 'job-categories', { name: `Software ${marker}`, description: marker }],
      ['skill', 'job-skills', { name: `Java ${marker}`, category: 'PROGRAMMING_LANGUAGE' }],
      ['tag', 'job-tags', { name: `Smoke ${marker}` }],
    ]) await security(`Non-admin updates ${kind}`, 'PUT', `/api/${path}/${f[kind]?.id}`,
      { actor: 'candidate', body, expected: [403] }, Boolean(f[kind] && run.actors.candidate?.jwt));
    await security('Non-admin deletes user', 'DELETE', `/api/users/${run.actors.otherEmployer?.id}`,
      { actor: 'candidate', expected: [403] }, Boolean(run.actors.otherEmployer && run.actors.candidate?.jwt));
    await run.stage('Security: JWT identity overrides forged headers', async () => {
      const profile = await run.json('GET', '/api/users/profile', { actor: 'candidate',
        headers: { 'X-User-Id': String(run.actors.employer.id), 'X-User-Email': run.actors.employer.email, 'X-User-Role': 'ROLE_ADMIN' } });
      run.check('Profile identity follows JWT', profile.id === run.actors.candidate.id && profile.role === 'ROLE_JOB_SEEKER');
      await run.request('GET', `/api/applications/${f.application.id}`, { actor: 'otherCandidate',
        headers: { 'X-User-Id': String(run.actors.candidate.id) }, expected: [403, 404] });
    }, Boolean(f.application && run.actors.otherCandidate?.jwt && run.actors.employer));

    await run.stage('Withdrawal and closed job rejection', async () => {
      await mutate('PATCH', `/api/applications/${f.application.id}/withdraw`, 'candidate', { reason: marker });
      run.check('Withdrawal persisted', (await get(`/api/applications/${f.application.id}`)).status === 'WITHDRAWN');
      await mutate('PATCH', `/api/jobs/${f.job.id}/close`, 'employer');
      run.check('Job close persisted', (await get(`/api/jobs/${f.job.id}`)).status === 'CLOSED');
      const response = await run.request('POST', '/api/applications', { actor: 'otherCandidate', probe: true,
        body: { jobId: f.job.id, resumeId: f.otherCandidateResume.id, coverLetter: `Closed job ${marker}` } });
      // If the defect creates an application, register it before reporting failure.
      if (positive(response.json?.id)) await run.remember('application', response.json.id, `/api/applications/${response.json.id}`, 'otherCandidate',
        { jobId: f.job.id, resumeId: f.otherCandidateResume.id });
      run.check('Closed job rejects a new candidate', response.status === 400 || response.status === 409 || response.status === 422,
        `actual HTTP ${response.status}`);
    }, Boolean(f.application && f.otherCandidateResume && run.actors.otherCandidate?.jwt));

    await run.stage('Administrator moderation', async () => {
      const user = run.actors.otherCandidate;
      await mutate('PATCH', `/api/users/${user.id}/suspend`, 'admin');
      run.check('User suspension persisted', (await get(`/api/users/${user.id}`, 'admin')).status === 'SUSPENDED');
      await run.request('POST', '/auth/login', { body: { email: user.email, password: cfg.smokePassword }, expected: [403] });
      await mutate('PATCH', `/api/users/${user.id}/activate`, 'admin');
      run.check('User activation persisted', (await get(`/api/users/${user.id}`, 'admin')).status === 'ACTIVE');
      const auth = await mutate('POST', '/auth/login', undefined, { email: user.email, password: cfg.smokePassword });
      run.check('Reactivated account can log in', auth.user?.id === user.id && Boolean(auth.jwt));
      await mutate('PATCH', `/api/companies/${f.company.id}/deactivate`, 'admin');
      const company = await get(`/api/companies/${f.company.id}`, 'admin');
      run.check('Company deactivation persisted', company.status === 'SUSPENDED');
    }, Boolean(f.company && run.actors.otherCandidate));
  } finally {
    await run.cleanup();
  }
}
