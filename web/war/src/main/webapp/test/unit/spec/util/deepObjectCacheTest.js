define([
    'util/deepObjectCache'
], function(DeepObjectCache) {

    describe('DeepObjectCache', function() {
        before(function() {
            this.callFn = function(...args) {
                return this.cache.getOrUpdate(this[`fn${args.length}Arg`], ...args)
            }
        })

        beforeEach(function() {
            this.cache = new DeepObjectCache()
            for (let i = 0; i < 4; i++) {
                this[`fn${i}Arg`] = sinon.spy((...args) => {
                    return args.reduce((result, a) => {
                        if (result) {
                            return result + '|' + a.result
                        }
                        return a.result
                    }, '')
                })
            }
        })

        it('should error when no new', function() {
            expect(DeepObjectCache).to.throw('new')
        })

        it('should clear', function() {
            const times = 3;
            const arg = { result: 'x' }
            for (let i = 0; i < times; i++) {
                this.callFn(arg)
            }
            this.fn1Arg.callCount.should.equal(1)

            for (let i = 0; i < times; i++) {
                this.cache.clear();
                this.callFn(arg)
            }
            this.fn1Arg.callCount.should.equal(times + 1)
        })

        it('should evaluate when no cache', function() {
            let result = this.callFn({ result: 'x'})
            result.should.equal('x')
            this.fn1Arg.callCount.should.equal(1)
        })

        it('should only evaluate once when no changes', function() {
            const p1 = { result: 'x'}
            let result = this.callFn(p1)
            let result2 = this.callFn(p1)
            result.should.equal('x')
            result.should.equal(result2)
            this.fn1Arg.callCount.should.equal(1)
        })
        
        it('should re-evaluate when args change', function() {
            const p1 = { result: 'x'}
            const p2 = { result: 'y'}
            let result = this.callFn(p1)
            let result2 = this.callFn(p2)
            result.should.equal('x')
            result2.should.equal('y')
            this.fn1Arg.callCount.should.equal(2)
        })

        it('should re-evaluate when args are equal but not identity', function() {
            const p1 = { result: 'x'}
            const p2 = { result: 'x'}
            let result = this.callFn(p1)
            let result2 = this.callFn(p2)
            result.should.equal('x')
            result2.should.equal('x')
            this.fn1Arg.callCount.should.equal(2)
        })

        it('should re-evaluate when args arity changes (less)', function() {
            const fn = sinon.spy(function(...args) {
                return args.map(a => a.result).join(',')
            })
            const p1 = { result: 'x'}
            const p2 = { result: 'y'}
            let result = this.cache.getOrUpdate(fn, p1, p2)
            let result2 = this.cache.getOrUpdate(fn, p1)
            let result3 = this.cache.getOrUpdate(fn, p1, p2)
            let result4 = this.cache.getOrUpdate(fn, p2, p1)
            result.should.equal('x,y')
            result2.should.equal('x')
            result3.should.equal('x,y')
            result4.should.equal('y,x')
            fn.callCount.should.equal(4)
        })

        it('should re-evaluate when args arity changes (more)', function() {
            const fn = sinon.spy(function(...args) {
                return args.map(a => a.result).join(',')
            })
            const p1 = { result: 'x'}
            const p2 = { result: 'y'}
            let result = this.cache.getOrUpdate(fn, p1)
            let result2 = this.cache.getOrUpdate(fn, p1, p2)
            result.should.equal('x')
            result2.should.equal('x,y')
            fn.callCount.should.equal(2)
        })

        it('should support value args', function() {
            const fn = sinon.spy(function(...args) {
                return args.join(',')
            })
            /* eslint no-new-wrappers:0 */
            let result = this.cache.getOrUpdate(fn, 'testing', 123, false, 32.5, undefined, null)
            let result2 = this.cache.getOrUpdate(fn, new String('testing'), 123, false, 32.5, undefined, null)
            result.should.equal('testing,123,false,32.5,,')
            result2.should.equal('testing,123,false,32.5,,')
            fn.callCount.should.equal(1)
        })

        it('should support value args changing', function() {
            const fn = sinon.spy(function(...args) {
                return args.join(',')
            })
            /* eslint no-new-wrappers:0 */
            let result = this.cache.getOrUpdate(fn, 'testing', 123, false, 32.5, undefined, null)
            let result2 = this.cache.getOrUpdate(fn, new String('testing'), 124, false, 32.5, undefined, null)
            result.should.equal('testing,123,false,32.5,,')
            result2.should.equal('testing,124,false,32.5,,')
            fn.callCount.should.equal(2)
        })

        it('should support object and value args', function() {
            const fn = sinon.spy(function(...args) {
                return args.map(a => a.result || a).join(',')
            })
            const p1 = { result: '0' }
            const p2 = { result: '1' }
            let result = this.cache.getOrUpdate(fn, p1, 2, p2, false)
            let result2 = this.cache.getOrUpdate(fn, p1, 2, p2, false)
            result.should.equal('0,2,1,false')
            result2.should.equal('0,2,1,false')
            fn.callCount.should.equal(1)
        })

        it('should support object and value args, value root', function() {
            const fn = sinon.spy(function(...args) {
                return args.map(a => a && a.result || a).join(',')
            })
            const p1 = 'testing'
            let result = this.cache.getOrUpdate(fn, 3, p1, true)
            let result2 = this.cache.getOrUpdate(fn, 3, p1, true)
            result.should.equal('3,testing,true')
            result2.should.equal('3,testing,true')
            fn.callCount.should.equal(1)

            let result3 = this.cache.getOrUpdate(fn, 3, p1, true, undefined)
            result3.should.equal('3,testing,true,')
            fn.callCount.should.equal(2)
        })
    })

})
